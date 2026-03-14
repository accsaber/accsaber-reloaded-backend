package com.accsaber.backend.model.entity.map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MapVoteActionConverter implements AttributeConverter<MapVoteAction, String> {

    @Override
    public String convertToDatabaseColumn(MapVoteAction action) {
        if (action == null)
            return null;
        return switch (action) {
            case RANK -> "rank";
            case REWEIGHT -> "reweight";
            case UNRANK -> "unrank";
        };
    }

    @Override
    public MapVoteAction convertToEntityAttribute(String dbData) {
        if (dbData == null)
            return null;
        return switch (dbData) {
            case "rank" -> MapVoteAction.RANK;
            case "reweight" -> MapVoteAction.REWEIGHT;
            case "unrank" -> MapVoteAction.UNRANK;
            default -> throw new IllegalArgumentException("Unknown map vote action: " + dbData);
        };
    }
}
