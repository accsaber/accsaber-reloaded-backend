package com.accsaber.backend.client;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.accsaber.backend.config.ComplexityModelProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ComplexityModelClient {

    private final WebClient webClient;
    private final ComplexityModelProperties properties;

    public ComplexityModelClient(@Qualifier("complexityModelWebClient") WebClient webClient,
            ComplexityModelProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public Optional<NoteAccuracies> noteAccuracies(byte[] zipBytes, String difficulty, String characteristic) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("zip", new ByteArrayResource(zipBytes) {
            @Override
            public String getFilename() {
                return "map.zip";
            }
        }).contentType(MediaType.APPLICATION_OCTET_STREAM);
        builder.part("difficulty", difficulty);
        builder.part("characteristic", characteristic);
        try {
            return Optional.ofNullable(webClient.post()
                    .uri("/note-accuracies")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(NoteAccuracies.class)
                    .block(Duration.ofMillis(properties.getTimeoutMs())));
        } catch (Exception e) {
            log.error("Complexity model call failed for {} {}: {}", characteristic, difficulty, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Health> health() {
        try {
            return Optional.ofNullable(webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Health.class)
                    .block(Duration.ofMillis(properties.getTimeoutMs())));
        } catch (Exception e) {
            log.warn("Complexity model health check failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Health {
        private String status;
        private String model;
        private String modelHash;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NoteAccuracies {
        private String model;
        private String modelHash;
        private String mapVersion;
        private double njs;
        private int notes;
        private int predictedNotes;
        private double meanAccuracy;
        private List<Double> noteAccuracies;
        private double resetShare;
        private double dotShare;
        private double bottomUpShare;
    }
}
