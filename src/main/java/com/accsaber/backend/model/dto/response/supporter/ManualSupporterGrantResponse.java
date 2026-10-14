package com.accsaber.backend.model.dto.response.supporter;

public record ManualSupporterGrantResponse(String kofiTransactionId, String userId, String tier, Integer amountCents,
        String type) {
}
