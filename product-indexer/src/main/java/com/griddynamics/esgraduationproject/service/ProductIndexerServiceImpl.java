package com.griddynamics.esgraduationproject.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProductIndexerServiceImpl implements ProductIndexerService {

    private final RestHighLevelClient esClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${com.griddynamics.es.graduation.project.files.mappings:classpath:elastic/typeaheads/mappings.json}")
    private Resource productMappingsFile;
    @Value("${com.griddynamics.es.graduation.project.files.settings:classpath:elastic/typeaheads/settings.json}")
    private Resource productSettingsFile;

    public ProductIndexerServiceImpl(RestHighLevelClient esClient) {
        this.esClient = esClient;
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
                .sorted(Comparator.reverseOrder())
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
