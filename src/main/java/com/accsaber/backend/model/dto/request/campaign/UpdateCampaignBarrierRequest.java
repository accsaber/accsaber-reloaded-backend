package com.accsaber.backend.model.dto.request.campaign;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.CampaignLabelPosition;
import com.accsaber.backend.validation.CleanText;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCampaignBarrierRequest {

    private BarrierConditionType conditionType;

    private BigDecimal conditionValue;

    @Size(max = 1000)
    @CleanText
    private String description;

    @Size(max = 80)
    @CleanText
    private String checkpointLabel;

    private CampaignLabelPosition checkpointLabelPosition;

    @Size(max = 512)
    @Pattern(regexp = "^$|^https?://[^\\s\"'<>]+$", message = "must be a valid http(s) URL")
    private String checkpointAvatarUrl;

    @Pattern(regexp = "^$|^#?[A-Za-z0-9]{1,32}$", message = "must be a hex or named color")
    private String checkpointColor;

    @Pattern(regexp = "^$|^#?[A-Za-z0-9]{1,32}$", message = "must be a hex or named color")
    private String borderColor;

    @Pattern(regexp = "^$|^[A-Za-z0-9 _-]{1,32}$", message = "invalid style token")
    private String borderShape;

    @Pattern(regexp = "^$|^[A-Za-z0-9 _-]{1,32}$", message = "invalid style token")
    private String size;

    @Pattern(regexp = "^$|^[A-Za-z0-9 _-]{1,32}$", message = "invalid style token")
    private String checkpointSize;

    private Integer positionX;

    private Integer positionY;

    private BigDecimal xp;

    @Size(max = 25)
    private List<UUID> prerequisiteCampaignDifficultyIds;

    @Size(max = 100)
    private List<UUID> affectedCampaignDifficultyIds;
}
