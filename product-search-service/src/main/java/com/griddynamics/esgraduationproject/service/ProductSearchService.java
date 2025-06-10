package com.griddynamics.esgraduationproject.service;

import com.griddynamics.esgraduationproject.model.ProductSearchRequest;
import com.griddynamics.esgraduationproject.model.ProductSearchResponse;

import java.io.IOException;

public interface ProductSearchService {
    ProductSearchResponse getServiceResponse(ProductSearchRequest request) throws IOException;

    void recreateIndex() throws IOException;
}
