package com.accsaber.backend.model.admin;

import java.util.function.Function;

import com.accsaber.backend.model.dto.request.admin.RunJobRequest;

public record JobField(
        String key,
        JobFieldKind kind,
        boolean required,
        boolean multiple,
        String label,
        String description,
        Function<RunJobRequest, Object> reader) {

    public static JobField required(String key, JobFieldKind kind, String label,
            Function<RunJobRequest, Object> reader) {
        return new JobField(key, kind, true, false, label, null, reader);
    }

    public static JobField requiredList(String key, JobFieldKind kind, String label,
            Function<RunJobRequest, Object> reader) {
        return new JobField(key, kind, true, true, label, null, reader);
    }

    public static JobField optional(String key, JobFieldKind kind, String label, String description,
            Function<RunJobRequest, Object> reader) {
        return new JobField(key, kind, false, false, label, description, reader);
    }
}
