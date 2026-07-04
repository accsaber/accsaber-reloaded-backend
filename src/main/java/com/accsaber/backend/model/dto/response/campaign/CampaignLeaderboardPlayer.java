package com.accsaber.backend.model.dto.response.campaign;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignLeaderboardPlayer {

    private String userId;
    private String userName;
    private String country;
    private String avatarUrl;
    private String cdnAvatarUrl;
}
