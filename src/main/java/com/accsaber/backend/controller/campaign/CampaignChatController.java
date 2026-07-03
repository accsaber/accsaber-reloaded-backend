package com.accsaber.backend.controller.campaign;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.campaign.SendCampaignChatMessageRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignChatMessageResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.campaign.CampaignChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign chat")
public class CampaignChatController {

    private final CampaignChatService campaignChatService;

    @Operation(summary = "List a campaign's chat messages (owner or collaborator)")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{campaignId}/chat")
    public ResponseEntity<Page<CampaignChatMessageResponse>> listMessages(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(campaignChatService.getMessages(principal.getUserId(), campaignId, pageable));
    }

    @Operation(summary = "Send a chat message to a campaign (owner or collaborator)")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/chat")
    public ResponseEntity<CampaignChatMessageResponse> sendMessage(
            @PathVariable UUID campaignId,
            @Valid @RequestBody SendCampaignChatMessageRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignChatService.sendMessage(principal.getUserId(), campaignId, request.getContent()));
    }
}
