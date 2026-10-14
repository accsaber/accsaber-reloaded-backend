package com.accsaber.backend.model.entity.map;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BatchStatusConverter extends DbEnumConverter<BatchStatus> {

    public BatchStatusConverter() {
        super(BatchStatus::getDbValue, BatchStatus::fromDbValue);
    }
}
