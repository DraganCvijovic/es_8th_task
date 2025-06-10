package com.griddynamics.esgraduationproject.rest;

import com.griddynamics.esgraduationproject.model.ProductSearchRequest;
import com.griddynamics.esgraduationproject.model.ProductSearchResponse;
import com.griddynamics.esgraduationproject.service.ProductSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/product")
public class ProductController {

    @Autowired
    private ProductSearchService searchService;

    @PostMapping
    public ProductSearchResponse search(@RequestBody ProductSearchRequest req) throws IOException {
        if (req.getTextQuery() == null || req.getTextQuery().isBlank()) {
            return new ProductSearchResponse(0, List.of(), Map.of());
        }
        return searchService.getServiceResponse(req);
    }
}
