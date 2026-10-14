package com.accsaber.backend.model.entity.map;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DifficultyConverter extends DbEnumConverter<Difficulty> {

    public DifficultyConverter() {
        super(Difficulty::getDbValue, Difficulty::fromDbValue);
    }
}
