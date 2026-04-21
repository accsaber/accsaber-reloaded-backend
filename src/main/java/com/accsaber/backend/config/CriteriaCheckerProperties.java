package com.accsaber.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "accsaber.criteria-checker")
public class CriteriaCheckerProperties {

    private String baseUrl;
    private int timeoutMs = 60000;
}
