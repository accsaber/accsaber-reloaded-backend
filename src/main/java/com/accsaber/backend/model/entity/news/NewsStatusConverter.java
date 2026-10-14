package com.accsaber.backend.model.entity.news;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NewsStatusConverter extends DbEnumConverter<NewsStatus> {

    public NewsStatusConverter() {
        super(NewsStatus::getDbValue, NewsStatus::fromDbValue);
    }
}
