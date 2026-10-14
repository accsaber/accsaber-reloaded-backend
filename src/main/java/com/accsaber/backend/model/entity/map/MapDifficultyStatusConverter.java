package com.accsaber.backend.model.entity.map;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MapDifficultyStatusConverter extends DbEnumConverter<MapDifficultyStatus> {

    public MapDifficultyStatusConverter() {
        super(MapDifficultyStatus::getDbValue, MapDifficultyStatus::fromDbValue);
    }
}
