package com.accsaber.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "accsaber.complexity-model")
public class ComplexityModelProperties {

    private String baseUrl;
    private int timeoutMs = 60000;
}
