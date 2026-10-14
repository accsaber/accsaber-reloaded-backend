package com.accsaber.backend.model.entity;

import java.util.function.Function;

import jakarta.persistence.AttributeConverter;

public abstract class DbEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Function<E, String> toDatabase;
    private final Function<String, E> fromDatabase;

    protected DbEnumConverter(Function<E, String> toDatabase, Function<String, E> fromDatabase) {
        this.toDatabase = toDatabase;
        this.fromDatabase = fromDatabase;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : toDatabase.apply(attribute);
    }

    @Override
    public E convertToEntityAttribute(String value) {
        return value == null ? null : fromDatabase.apply(value);
    }
}
