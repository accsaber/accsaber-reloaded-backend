package com.accsaber.backend.model.dto.response.chat;

import java.time.Instant;

public record ChatUnreadResponse(int count, Instant latestAt) {
}
