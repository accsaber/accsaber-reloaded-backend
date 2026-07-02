package com.accsaber.backend.websocket.server;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignPresenceMessage {

    private UUID campaignId;
    private Long actorUserId;
    private String actorName;
    private String actorAvatarUrl;
    private String type;
    private String targetId;
    private Double x;
    private Double y;
    private String field;
    private Long ts;
    private List<Member> members;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Member {
        private Long userId;
        private String name;
        private String avatarUrl;
    }
}
