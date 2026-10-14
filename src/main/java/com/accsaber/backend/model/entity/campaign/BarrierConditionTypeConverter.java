package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BarrierConditionTypeConverter extends DbEnumConverter<BarrierConditionType> {

    public BarrierConditionTypeConverter() {
        super(Enum::name, BarrierConditionType::valueOf);
    }
}
