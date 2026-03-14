package com.accsaber.backend.model.dto;

import java.util.List;

public record MilestoneQuerySpec(
        SelectSpec select,
        String from,
        List<FilterSpec> filters) {

    public record SelectSpec(String function, String column) {
    }

    public record FilterSpec(String column, String operator, Object value, MilestoneQuerySpec subquery) {

        public FilterSpec(String column, String operator, Object value) {
            this(column, operator, value, null);
        }
    }
}
