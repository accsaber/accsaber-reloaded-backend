package com.accsaber.backend.model.dto.response.map;

import org.springframework.data.domain.Page;

import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;

public record MapHubResponse<D>(
        D difficulty,
        VoteListResponse votes,
        Page<ChatMessageResponse> comments,
        Boolean subscribed) {
}
