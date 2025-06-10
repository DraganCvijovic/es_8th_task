package com.griddynamics.esgraduationproject;

import com.griddynamics.esgraduationproject.service.ProductSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
@Slf4j
public class Application implements CommandLineRunner {

    private final ProductSearchService service;

    public Application(ProductSearchService service) {
        this.service = service;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws IOException {
        service.recreateIndex();
    }
}