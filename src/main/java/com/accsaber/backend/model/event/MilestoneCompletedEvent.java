package com.accsaber.backend.model.event;

import com.accsaber.backend.model.dto.response.milestone.MilestoneCompletedResponse;

public record MilestoneCompletedEvent(MilestoneCompletedResponse payload) {
}
