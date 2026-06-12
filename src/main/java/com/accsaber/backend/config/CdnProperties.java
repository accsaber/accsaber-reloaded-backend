package com.accsaber.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "accsaber.cdn")
public class CdnProperties {

    private String storagePath = "./data/cdn";
    private String baseUrl = "http://localhost:8080/cdn";
    private String vipsBinary = "vips";
    private long backfillDelayMs = 0L;
    private long maxUploadBytes = 10L * 1024L * 1024L;
    private int webpQuality = 80;
    private int webpEffort = 4;
    private int avifQuality = 60;
    private int avifEffort = 4;
    private int avatarMaxDimension = 256;
    private int coverMaxDimension = 1024;
    private int uploadMaxDimension = 4096;
    private long encodeTimeoutMs = 30_000L;
}
