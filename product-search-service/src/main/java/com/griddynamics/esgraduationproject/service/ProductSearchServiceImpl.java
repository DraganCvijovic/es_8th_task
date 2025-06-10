package com.griddynamics.esgraduationproject.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.griddynamics.esgraduationproject.model.FacetBucket;
import com.griddynamics.esgraduationproject.model.ProductSearchRequest;
import com.griddynamics.esgraduationproject.model.ProductSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequest;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final String INDEX_ALIAS = "product_index";
    private final RestHighLevelClient esClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${com.griddynamics.es.graduation.project.files.mappings:classpath:elastic/typeaheads/mappings.json}")
    private Resource productMappingsFile;
    @Value("${com.griddynamics.es.graduation.project.files.settings:classpath:elastic/typeaheads/settings.json}")
    private Resource productSettingsFile;

    public ProductSearchServiceImpl(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    @Override
    public ProductSearchResponse getServiceResponse(ProductSearchRequest request) throws IOException {
        if (request.getTextQuery() == null || request.getTextQuery().trim().isEmpty()) {
            return new ProductSearchResponse(0, Collections.emptyList(), Collections.emptyMap());
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // Build query
        BoolQueryBuilder boolQuery = buildQuery(request.getTextQuery());
        sourceBuilder.query(boolQuery);

        // Add sorting
        sourceBuilder.sort("_score", SortOrder.DESC);
        sourceBuilder.sort("id", SortOrder.DESC);

        // Add pagination
        sourceBuilder.from(request.getPage() * request.getSize());
        sourceBuilder.size(request.getSize());

        // Add aggregations
        addAggregations(sourceBuilder);

        org.elasticsearch.action.search.SearchRequest searchRequest =
                new org.elasticsearch.action.search.SearchRequest(INDEX_ALIAS)
                        .source(sourceBuilder);

        SearchResponse response =
                esClient.search(searchRequest, RequestOptions.DEFAULT);

        return mapResponse(response);

    }

    private void addAggregations(SearchSourceBuilder sourceBuilder) {
        // Brand aggregation
        sourceBuilder.aggregation(AggregationBuilders.terms("brand")
                .field("brand.keyword")
                .order(BucketOrder.compound(BucketOrder.count(false), BucketOrder.key(true)))
                .size(10));

        // Price range aggregation
        sourceBuilder.aggregation(AggregationBuilders.range("price")
                .field("price")
                .addRange("Cheap", 0, 100)
                .addRange("Average", 100, 500)
                .addRange("Expensive", 500, Double.MAX_VALUE));

        // Color aggregation (nested)
        sourceBuilder.aggregation(AggregationBuilders.nested("color_nested", "skus")
                .subAggregation(AggregationBuilders.terms("color")
                        .field("skus.color")
                        .order(BucketOrder.compound(BucketOrder.count(false), BucketOrder.key(true)))));

        // Size aggregation (nested)
        sourceBuilder.aggregation(AggregationBuilders.nested("size_nested", "skus")
                .subAggregation(AggregationBuilders.terms("size")
                        .field("skus.size")
                        .order(BucketOrder.compound(BucketOrder.count(false), BucketOrder.key(true)))));
    }

    private ProductSearchResponse mapResponse(org.elasticsearch.action.search.SearchResponse esResponse) {
        long totalHits = esResponse.getHits().getTotalHits().value;

        // Map products (_source)
        List<Map<String, Object>> products = Arrays.stream(esResponse.getHits().getHits())
                .map(hit -> {
                    Map<String, Object> source = hit.getSourceAsMap();
                    source.put("id", hit.getId()); // Include document ID if needed
                    return source;
                })
                .collect(Collectors.toList());

        // Map facets
        Map<String, List<FacetBucket>> facets = new HashMap<>();

        // 1. Brand facet
        Terms brandTerms = esResponse.getAggregations().get("brand");
        facets.put("brand", brandTerms.getBuckets().stream()
                .map(b -> new FacetBucket(b.getKeyAsString(), b.getDocCount()))
                .collect(Collectors.toList()));

        // 2. Price range facet
        Range priceRange = esResponse.getAggregations().get("price");
        facets.put("price", priceRange.getBuckets().stream()
                .map(b -> new FacetBucket(b.getKeyAsString(), b.getDocCount()))
                .collect(Collectors.toList()));

        // 3. Color facet (nested)
        ParsedNested colorNested = esResponse.getAggregations().get("color_nested");
        Terms colorTerms = colorNested.getAggregations().get("color");
        facets.put("color", colorTerms.getBuckets().stream()
                .map(b -> new FacetBucket(b.getKeyAsString(), b.getDocCount()))
                .collect(Collectors.toList()));

        // 4. Size facet (nested)
        ParsedNested sizeNested = esResponse.getAggregations().get("size_nested");
        Terms sizeTerms = sizeNested.getAggregations().get("size");
        facets.put("size", sizeTerms.getBuckets().stream()
                .map(b -> new FacetBucket(b.getKeyAsString(), b.getDocCount()))
                .collect(Collectors.toList()));

        return new ProductSearchResponse(totalHits, products, facets);
    }

    private BoolQueryBuilder buildQuery(String queryText) throws IOException {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // Analyze the query text
        AnalyzeRequest analyzeRequest = new AnalyzeRequest()
                .index(INDEX_ALIAS)
                .analyzer("text_analyzer")
                .text(queryText);

        AnalyzeResponse analyzeResponse = esClient.indices().analyze(analyzeRequest, RequestOptions.DEFAULT);
        List<String> tokens = analyzeResponse.getTokens().stream()
                .map(AnalyzeResponse.AnalyzeToken::getTerm)
                .collect(Collectors.toList());

        if (tokens.isEmpty()) {
            return boolQuery;
        }

        // Build queries for each token
        for (String token : tokens) {
            BoolQueryBuilder tokenQuery = QueryBuilders.boolQuery();

            // Check if token is a size
            if (Arrays.asList("xxs", "xs", "s", "m", "l", "xl", "xxl", "xxxl").contains(token.toLowerCase())) {
                tokenQuery.should(QueryBuilders.nestedQuery("skus",
                                QueryBuilders.termQuery("skus.size", token), ScoreMode.Total))
                        .boost(2f);
            }
            // Check if token is a color
            else if (Arrays.asList("green", "black", "white", "blue", "yellow",
                    "red", "brown", "orange", "grey").contains(token.toLowerCase())) {
                tokenQuery.should(QueryBuilders.nestedQuery("skus",
                                QueryBuilders.termQuery("skus.color", token), ScoreMode.Total))
                        .boost(3f);
            }
            // For other tokens search in brand and name
            else {
                // Standard search in brand and name
                tokenQuery.should(QueryBuilders.multiMatchQuery(token, "brand", "name")
                        .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
                        .operator(Operator.AND));

                // Boost for shingles
                tokenQuery.should(QueryBuilders.multiMatchQuery(token, "brand.shingles", "name.shingles")
                        .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
                        .boost(5f));
            }

            boolQuery.must(tokenQuery);
        }

        return boolQuery;
    }

    public void recreateIndex() throws IOException {
        String alias = "product_index";
        String newIndexName = alias + "_" + System.currentTimeMillis();

        // 1. Create new index with settings and mappings
        String settings = getStrFromResource(productSettingsFile);
        String mappings = getStrFromResource(productMappingsFile);
        createIndex(newIndexName, settings, mappings);

        // 2. Bulk index data
        bulkIndex(newIndexName);

        // 3. Update alias
        updateAlias(alias, newIndexName);

        // 4. Clean up old indices
        cleanOldIndices(alias, 3);
    }

    private void bulkIndex(String indexName) throws IOException {
        BulkRequest bulkRequest = new BulkRequest();
        InputStream is = getClass().getClassLoader().getResourceAsStream("task_8_data.json");
        List<Map<String, Object>> products = objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
        for (Map<String, Object> product : products) {
            bulkRequest.add(new IndexRequest(indexName).source(product));
        }
        esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
    }

    private void updateAlias(String alias, String newIndex) throws IOException {
        GetAliasesRequest getAliasesRequest = new GetAliasesRequest(alias);
        Set<String> oldIndices = esClient.indices().getAlias(getAliasesRequest, RequestOptions.DEFAULT)
                .getAliases().keySet();

        IndicesAliasesRequest request = new IndicesAliasesRequest();
        for (String oldIndex : oldIndices) {
            request.addAliasAction(new AliasActions(AliasActions.Type.REMOVE).index(oldIndex).alias(alias));
        }
        request.addAliasAction(new AliasActions(AliasActions.Type.ADD).index(newIndex).alias(alias));

        esClient.indices().updateAliases(request, RequestOptions.DEFAULT);
    }

    private void cleanOldIndices(String alias, int maxIndices) throws IOException {
        GetIndexRequest request = new GetIndexRequest(alias + "_*");
        String[] allIndices = esClient.indices().get(request, RequestOptions.DEFAULT).getIndices();
        List<String> sorted = Arrays.stream(allIndices)
                .sorted(Comparator.reverseOrder()) // newest first
                .collect(Collectors.toList());

        List<String> toDelete = sorted.stream().skip(maxIndices).collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(toDelete.toArray(new String[0]));
            esClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
        }
    }

    private static String getStrFromResource(Resource resource) {
        try {
            if (!resource.exists()) {
                throw new IllegalArgumentException("File not found: " + resource.getFilename());
            }
            return Resources.toString(resource.getURL(), Charsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Can not read resource file: " + resource.getFilename(), ex);
        }
    }

    private void createIndex(String indexName, String settings, String mappings) {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName)
                .settings(settings, XContentType.JSON)
                .mapping(mappings, XContentType.JSON);

        CreateIndexResponse createIndexResponse;
        try {
            createIndexResponse = esClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
        } catch (IOException ex) {
            throw new RuntimeException("An error occurred during creating ES index.", ex);
        }

        if (!createIndexResponse.isAcknowledged()) {
            throw new RuntimeException("Creating index not acknowledged for indexName: " + indexName);
        } else {
            log.info("Index {} has been created.", indexName);
        }
    }
}
