package com.accsaber.backend.model.dto.request.map;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record UpdateMapCategoryRequest(
        @NotNull UUID categoryId) {
}
