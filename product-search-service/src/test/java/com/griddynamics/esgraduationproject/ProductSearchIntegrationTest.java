/*
package com.griddynamics.esgraduationproject;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ProductSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testEmptyResponse() throws Exception {
        // Test empty request
        mockMvc.perform(post("/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.products").isEmpty());

        // Test with non-matching query
        mockMvc.perform(post("/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"Calvin klein L blue ankle skinny jeans wrongword\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.products").isEmpty());

        // Test with non-existing color
        mockMvc.perform(post("/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"Calvin klein L red ankle skinny jeans\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.products").isEmpty());
    }

    @Test
    public void testHappyPath() throws Exception {
        mockMvc.perform(post("/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"Calvin klein L blue ankle skinny jeans\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].id").value("2"))
                .andExpect(jsonPath("$.products[0].brand").value("Calvin Klein"))
                .andExpect(jsonPath("$.products[0].name").value("Women ankle skinny jeans, model 1282"))
                .andExpect(jsonPath("$.products[0].skus.length()").value(9))
                .andExpect(jsonPath("$.facets.brand").exists())
                .andExpect(jsonPath("$.facets.price").exists())
                .andExpect(jsonPath("$.facets.color").exists())
                .andExpect(jsonPath("$.facets.size").exists());
    }

    @Test
    public void testFacets() throws Exception {
        mockMvc.perform(post("/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"jeans\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facets.brand.length()").value(2))
                .andExpect(jsonPath("$.facets.brand[0].value").value("Calvin Klein"))
                .andExpect(jsonPath("$.facets.brand[0].count").value(4))
                .andExpect(jsonPath("$.facets.brand[1].value").value("Levi's"))
                .andExpect(jsonPath("$.facets.brand[1].count").value(4))
                .andExpect(jsonPath("$.facets.price.length()").value(3))
                .andExpect(jsonPath("$.facets.price[0].value").value("Cheap"))
                .andExpect(jsonPath("$.facets.price[0].count").value(2))
                .andExpect(jsonPath("$.facets.price[1].value").value("Average"))
                .andExpect(jsonPath("$.facets.price[1].count").value(6))
                .andExpect(jsonPath("$.facets.price[2].value").value("Expensive"))
                .andExpect(jsonPath("$.facets.price[2].count").value(0))
                .andExpect(jsonPath("$.facets.color.length()").value(4))
                .andExpect(jsonPath("$.facets.color[0].value").value("Blue"))
                .andExpect(jsonPath("$.facets.color[0].count").value(8))
                .andExpect(jsonPath("$.facets.color[1].value").value("Black"))
                .andExpect(jsonPath("$.facets.color[1].count").value(7))
                .andExpect(jsonPath("$.facets.color[2].value").value("Red"))
                .andExpect(jsonPath("$.facets.color[2].count").value(1))
                .andExpect(jsonPath("$.facets.color[3].value").value("White"))
                .andExpect(jsonPath("$.facets.color[3].count").value(1))
                .andExpect(jsonPath("$.facets.size.length()").value(6))
                .andExpect(jsonPath("$.facets.size[0].value").value("L"))
                .andExpect(jsonPath("$.facets.size[0].count").value(8))
                .andExpect(jsonPath("$.facets.size[1].value").value("M"))
                .andExpect(jsonPath("$.facets.size[1].count").value(8))
                .andExpect(jsonPath("$.facets.size[2].value").value("S"))
                .andExpect(jsonPath("$.facets.size[2].count").value(6))
                .andExpect(jsonPath("$.facets.size[3].value").value("XL"))
                .andExpect(jsonPath("$.facets.size[3].count").value(5))
                .andExpect(jsonPath("$.facets.size[4].value").value("XXL"))
                .andExpect(jsonPath("$.facets.size[4].count").value(3))
                .andExpect(jsonPath("$.facets.size[5].value").value("XS"))
                .andExpect(jsonPath("$.facets.size[5].count").value(2));
    }

    @Test
    public void testSortAndBoost() throws Exception {
        // Test basic sort
        mockMvc.perform(post("/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"jeans\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(8))
                .andExpect(jsonPath("$.products[0].id").value("8"))
                .andExpect(jsonPath("$.products[1].id").value("7"))
                .andExpect(jsonPath("$.products[2].id").value("6"))
                .andExpect(jsonPath("$.products[3].id").value("5"))
                .andExpect(jsonPath("$.products[4].id").value("4"))
                .andExpect(jsonPath("$.products[5].id").value("3"))
                .andExpect(jsonPath("$.products[6].id").value("2"))
                .andExpect(jsonPath("$.products[7].id").value("1"));

        // Test boost with different word orders
        mockMvc.perform(post("/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"blue WOMEN jeans\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(5))
                .andExpect(jsonPath("$.products[0].id").value("5"))
                .andExpect(jsonPath("$.products[1].id").value("3"))
                .andExpect(jsonPath("$.products[2].id").value("6"))
                .andExpect(jsonPath("$.products[3].id").value("2"))
                .andExpect(jsonPath("$.products[4].id").value("1"));
    }

    @Test
    public void testPagination() throws Exception {
        mockMvc.perform(post("/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"jeans\", \"size\":2, \"page\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(8))
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].id").value("6"))
                .andExpect(jsonPath("$.products[1].id").value("5"));
    }
}
*/
