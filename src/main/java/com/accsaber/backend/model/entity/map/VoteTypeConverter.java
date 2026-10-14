package com.accsaber.backend.model.entity.map;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VoteTypeConverter extends DbEnumConverter<VoteType> {

    public VoteTypeConverter() {
        super(VoteType::getDbValue, VoteType::fromDbValue);
    }
}
