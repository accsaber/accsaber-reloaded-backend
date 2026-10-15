package com.accsaber.backend.websocket.server;

import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignPresenceMessage {

    private UUID campaignId;
    private PlayerRef actor;
    private String type;
    private String targetId;
    private Double x;
    private Double y;
    private String field;
    private Long ts;
    private List<PlayerRef> members;
}
