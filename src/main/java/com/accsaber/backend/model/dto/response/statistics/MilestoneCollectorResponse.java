package com.accsaber.backend.model.dto.response.statistics;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MilestoneCollectorResponse {

    private String userId;
    private String userName;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private String country;
    private long milestoneCount;
}
