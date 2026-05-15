package com.accsaber.backend.model.dto.response.milestone;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MilestoneCompletedResponse {

    private Long userId;
    private String userName;
    private String userCountry;
    private String userAvatarUrl;
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
        private BigDecimal xp;
        private UUID awardsItemId;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompletedSet {
        private UUID id;
        private String title;
        private String description;
        private BigDecimal bonusXp;
        private UUID awardsItemId;
    }
}
