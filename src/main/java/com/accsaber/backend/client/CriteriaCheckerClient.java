package com.accsaber.backend.client;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.accsaber.backend.config.CriteriaCheckerProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CriteriaCheckerClient {

    private final WebClient webClient;
    private final CriteriaCheckerProperties properties;

    public CriteriaCheckerClient(@Qualifier("criteriaCheckerWebClient") WebClient webClient,
            CriteriaCheckerProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public CheckResult check(byte[] zipBytes, String difficulty, String category) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("zip", new ByteArrayResource(zipBytes) {
            @Override
            public String getFilename() {
                return "map.zip";
            }
        }).contentType(MediaType.APPLICATION_OCTET_STREAM);
        builder.part("difficulty", difficulty);
        builder.part("category", category);

        return webClient.post()
                .uri("/check")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(CheckResult.class)
                .block(Duration.ofMillis(properties.getTimeoutMs()));
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckResult {
        private String status;
        private List<String> failures;
    }
}
