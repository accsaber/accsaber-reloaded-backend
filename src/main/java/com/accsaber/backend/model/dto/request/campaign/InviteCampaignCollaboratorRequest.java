package com.accsaber.backend.model.dto.request.campaign;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteCampaignCollaboratorRequest {

    @NotNull
    private Long userId;
}
