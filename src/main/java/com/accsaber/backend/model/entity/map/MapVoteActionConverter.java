package com.accsaber.backend.model.entity.map;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MapVoteActionConverter extends DbEnumConverter<MapVoteAction> {

    public MapVoteActionConverter() {
        super(MapVoteAction::getDbValue, MapVoteAction::fromDbValue);
    }
}
