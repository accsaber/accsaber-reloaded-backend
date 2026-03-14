package com.accsaber.backend.model.dto.response.milestone;

import java.util.List;
import java.util.Map;

public record MilestoneSchemaResponse(
        Map<String, List<ColumnInfo>> tables,
        List<String> functions,
        List<String> operators) {

    public record ColumnInfo(String name, String type, List<String> enumValues) {
    }
}
