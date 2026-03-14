package com.accsaber.backend.util;

import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class MilestoneQuerySpecConverter implements AttributeConverter<MilestoneQuerySpec, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(MilestoneQuerySpec spec) {
        if (spec == null)
            return null;
        try {
            return MAPPER.writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize MilestoneQuerySpec", e);
        }
    }

    @Override
    public MilestoneQuerySpec convertToEntityAttribute(String json) {
        if (json == null || json.isBlank())
            return null;
        try {
            return MAPPER.readValue(json, MilestoneQuerySpec.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize MilestoneQuerySpec", e);
        }
    }
}
