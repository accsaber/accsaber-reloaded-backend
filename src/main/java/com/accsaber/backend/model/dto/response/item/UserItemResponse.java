package com.accsaber.backend.model.dto.response.item;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserItemResponse {

    private UUID linkId;
    private ItemResponse item;
    private List<ModifierRef> modifiers;
    private Long serialNumber;
    private Long quantity;
    private Map<String, Long> counters;
    private String source;
    private String sourceId;
    private UUID awardedByStaffId;
    private String reason;
    private Instant awardedAt;

    @Getter
    @Builder
    public static class ModifierRef {
        private UUID id;
        private String key;
        private String name;
        private String colorHex;
        private Object effectSpec;
    }
}
