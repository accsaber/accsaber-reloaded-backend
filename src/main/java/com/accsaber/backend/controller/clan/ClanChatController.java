package com.accsaber.backend.controller.clan;

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
import com.accsaber.backend.service.chat.ChatService;
import com.accsaber.backend.service.clan.ClanChatChannel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/clans")
@RequiredArgsConstructor
@Tag(name = "Clans")
public class ClanChatController {

    private final ChatService chatService;
    private final ClanChatChannel clanChatChannel;

    @Operation(summary = "Read a clan's chat",
            description = "Members only, newest first. Messages from members come with content. Things that happened to "
                    + "the clan come with an event instead, like member_joined, member_kicked or alliance_formed, where "
                    + "author is whoever did it, subject is the player it happened to and clan is the other clan "
                    + "involved. Connect to /ws/clans/chat?clanId=...&token=... to get both as they happen.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{clanId}/chat")
    public ResponseEntity<Page<ChatMessageResponse>> messages(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(chatService.getMessages(clanChatChannel, clanId, principal.getUserId(), pageable));
    }

    @Operation(summary = "Send a clan chat message", description = "Members only, and the chat rate limit applies.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{clanId}/chat")
    public ResponseEntity<ChatMessageResponse> send(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody SendChatMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                chatService.sendMessage(clanChatChannel, clanId, principal.getUserId(), request.getContent()));
    }
}
