package com.accsaber.backend.model.dto.response.campaign;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignDetailResponse {

    @JsonUnwrapped
    @JsonIgnoreProperties("difficultyCount")
    private CampaignResponse campaign;
    private String curatorNotes;
    private List<CampaignDifficultyResponse> difficulties;
    private List<CampaignBarrierResponse> barriers;
    private List<CampaignTextResponse> texts;
}
