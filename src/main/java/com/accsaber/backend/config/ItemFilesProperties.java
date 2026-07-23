package com.accsaber.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "accsaber.item-files")
public class ItemFilesProperties {

    private String storagePath = "./data/item-files";
    private String signingKey = "";
}
