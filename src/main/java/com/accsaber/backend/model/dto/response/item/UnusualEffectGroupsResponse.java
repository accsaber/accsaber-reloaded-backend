package com.accsaber.backend.model.dto.response.item;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UnusualEffectGroupsResponse {

    private List<CrateGroup> groups;
    private List<UnusualEffectResponse> ungrouped;

    @Getter
    @Builder
    public static class CrateGroup {

        private UUID crateId;
        private String crateName;
        private String crateIconUrl;
        private List<UnusualEffectResponse> effects;
    }
}
