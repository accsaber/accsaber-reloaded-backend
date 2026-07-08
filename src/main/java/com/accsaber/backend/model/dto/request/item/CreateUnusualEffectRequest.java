package com.accsaber.backend.model.dto.request.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUnusualEffectRequest {

    @NotBlank
    private String key;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private Object effectSpec;
}
