package com.accsaber.backend.model.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MilestoneQuerySpec(
        SelectSpec select,
        String from,
        List<FilterSpec> filters,
        HavingSpec having,
        MilestoneQuerySpec divisor,
        @JsonProperty("group_by") List<GroupBySpec> groupBy,
        @JsonProperty("outer_function") String outerFunction,
        @JsonProperty("order_by") List<OrderBySpec> orderBy,
        Integer limit,
        @JsonProperty("or_groups") List<List<FilterSpec>> orGroups,
        String scope) {

    public MilestoneQuerySpec(SelectSpec select, String from, List<FilterSpec> filters) {
        this(select, from, filters, null, null, null, null, null, null, null, null);
    }

    public MilestoneQuerySpec(SelectSpec select, String from, List<FilterSpec> filters,
            HavingSpec having, MilestoneQuerySpec divisor, List<GroupBySpec> groupBy,
            String outerFunction, List<OrderBySpec> orderBy, Integer limit) {
        this(select, from, filters, having, divisor, groupBy, outerFunction, orderBy, limit, null, null);
    }

    public MilestoneQuerySpec(SelectSpec select, String from, List<FilterSpec> filters,
            HavingSpec having, MilestoneQuerySpec divisor, List<GroupBySpec> groupBy,
            String outerFunction, List<OrderBySpec> orderBy, Integer limit,
            List<List<FilterSpec>> orGroups) {
        this(select, from, filters, having, divisor, groupBy, outerFunction, orderBy, limit, orGroups, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SelectSpec(String function, String column, Integer offset) {

        public SelectSpec(String function, String column) {
            this(function, column, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FilterSpec(String column, String operator, Object value, MilestoneQuerySpec subquery,
            TransformSpec transform, @JsonProperty("column_ref") String columnRef,
            @JsonProperty("column_ref_transform") TransformSpec columnRefTransform) {

        public FilterSpec(String column, String operator, Object value) {
            this(column, operator, value, null, null, null, null);
        }

        public FilterSpec(String column, String operator, Object value, MilestoneQuerySpec subquery) {
            this(column, operator, value, subquery, null, null, null);
        }

        public FilterSpec(String column, String operator, Object value, MilestoneQuerySpec subquery,
                TransformSpec transform) {
            this(column, operator, value, subquery, transform, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HavingSpec(String function, String column, String operator, Object value,
            @JsonProperty("value_query") MilestoneQuerySpec valueQuery) {

        public HavingSpec(String function, String column, String operator, Object value) {
            this(function, column, operator, value, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransformSpec(String function, Object argument) {
    }

    public record OrderBySpec(String column, String direction) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupBySpec(String column, String cast) {

        public GroupBySpec(String column) {
            this(column, null);
        }
    }
}
