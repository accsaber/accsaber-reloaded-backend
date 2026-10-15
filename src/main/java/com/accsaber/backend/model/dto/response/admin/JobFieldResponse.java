package com.accsaber.backend.model.dto.response.admin;

import com.accsaber.backend.model.admin.JobField;
import com.accsaber.backend.model.admin.JobFieldKind;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JobFieldResponse {

    String key;
    JobFieldKind kind;
    boolean required;
    boolean multiple;
    String label;
    String description;

    public static JobFieldResponse from(JobField field) {
        return JobFieldResponse.builder()
                .key(field.key())
                .kind(field.kind())
                .required(field.required())
                .multiple(field.multiple())
                .label(field.label())
                .description(field.description())
                .build();
    }
}
