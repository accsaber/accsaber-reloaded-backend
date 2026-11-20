package com.accsaber.backend.service.stats;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AliasFor;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Cacheable(value = "statistics", condition = "#filter.userIds() == null")
@interface CachedSiteStatistic {

    @AliasFor(annotation = Cacheable.class)
    String key();
}
