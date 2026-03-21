package com.accsaber.backend.model.dto.request.milestone;

import lombok.Data;

@Data
public class UpdateMilestoneRequest {

    private String title;

    private String description;
}
