package com.accsaber.backend.model.event;

import com.accsaber.backend.model.dto.response.item.CrateOpenBroadcast;

public record CrateOpenedEvent(CrateOpenBroadcast payload) {
}
