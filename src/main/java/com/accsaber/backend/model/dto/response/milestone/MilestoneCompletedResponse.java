package com.accsaber.backend.model.dto.response.milestone;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MilestoneCompletedResponse {

    private PlayerRef player;
    private Instant completedAt;
    private List<CompletedMilestone> milestones;
    private List<CompletedSet> sets;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompletedMilestone {
        private UUID id;
        private UUID setId;
        private UUID categoryId;
        private String title;
        private String description;
        private String type;
        private String tier;

        private String iconGroup;
        private Double xp;
        private List<MilestoneRewardResponse> rewards;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompletedSet {
        private UUID id;
        private String title;
        private String description;
        private Double bonusXp;
        private List<MilestoneRewardResponse> rewards;
    }
}
