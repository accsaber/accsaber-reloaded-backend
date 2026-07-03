package com.accsaber.backend.model.dto.request.campaign;

import com.accsaber.backend.validation.CleanText;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendCampaignChatMessageRequest {

    @NotBlank
    @Size(max = 2000)
    @CleanText
    private String content;
}
