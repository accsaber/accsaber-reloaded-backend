package com.accsaber.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "accsaber.complexity.rater")
public class ComplexityRaterProperties extends ComplexityRaterSpec {

    @JsonIgnore
    private String version = "note-acc-2026-09-30";
}
