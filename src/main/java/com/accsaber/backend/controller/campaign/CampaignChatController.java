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

import com.accsaber.backend.model.dto.request.chat.SendChatMessageRequest;
import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.campaign.CampaignChatChannel;
import com.accsaber.backend.service.chat.ChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns")
public class CampaignChatController {

    private final ChatService chatService;
    private final CampaignChatChannel campaignChatChannel;

    @Operation(summary = "Read a campaign's chat", description = "Messages between the people working on a campaign, newest last. Only the owner and collaborators can see it. There is a live version over the campaign presence socket if you would rather not poll.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{campaignId}/chat")
    public ResponseEntity<Page<ChatMessageResponse>> listMessages(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                chatService.getMessages(campaignChatChannel, campaignId, principal.getUserId(), pageable));
    }

    @Operation(summary = "Send a chat message", description = "Posts to a campaign's chat. Owner and collaborators only, and there is a rate limit so do not lean on it.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/chat")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable UUID campaignId,
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                chatService.sendMessage(campaignChatChannel, campaignId, principal.getUserId(), request.getContent()));
    }
}
