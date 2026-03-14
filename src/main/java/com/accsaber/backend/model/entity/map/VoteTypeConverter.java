package com.accsaber.backend.model.entity.map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VoteTypeConverter implements AttributeConverter<VoteType, String> {

    @Override
    public String convertToDatabaseColumn(VoteType voteType) {
        if (voteType == null)
            return null;
        return voteType.getDbValue();
    }

    @Override
    public VoteType convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return VoteType.fromDbValue(value);
    }
}
