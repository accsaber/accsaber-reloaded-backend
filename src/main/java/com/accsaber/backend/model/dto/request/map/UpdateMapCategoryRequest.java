package com.accsaber.backend.model.dto.request.map;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMapCategoryRequest {

    @NotNull
    private UUID categoryId;
}
