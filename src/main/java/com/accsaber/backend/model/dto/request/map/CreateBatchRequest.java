package com.accsaber.backend.model.dto.request.map;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBatchRequest {

    @NotBlank
    private String name;

    private String description;
}
