package com.griddynamics.esgraduationproject.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProductSearchResponse {
    private long totalHits;
    private List<Map<String, Object>> products;
    private Map<String, List<FacetBucket>> facets;
}
