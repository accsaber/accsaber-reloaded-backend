package com.accsaber.backend.model.dto.response.campaign;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonIgnoreProperties("difficultyCount")
public class CampaignDetailResponse extends CampaignResponse {

    private String curatorNotes;
    private List<CampaignDifficultyResponse> difficulties;
    private List<CampaignBarrierResponse> barriers;
    private List<CampaignTextResponse> texts;
}
