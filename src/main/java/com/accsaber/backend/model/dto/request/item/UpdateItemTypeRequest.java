package com.accsaber.backend.model.dto.request.item;

import java.util.Map;

public record UpdateItemTypeRequest(
        String name,
        String description,
        Map<String, Object> valueSchema) {
}
