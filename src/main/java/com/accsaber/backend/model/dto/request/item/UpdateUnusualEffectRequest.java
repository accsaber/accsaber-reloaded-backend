package com.accsaber.backend.model.dto.request.item;

import lombok.Data;

@Data
public class UpdateUnusualEffectRequest {

    private String name;
    private String description;
    private Object effectSpec;
}
