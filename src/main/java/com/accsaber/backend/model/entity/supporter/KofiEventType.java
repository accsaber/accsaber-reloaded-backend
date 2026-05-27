package com.accsaber.backend.model.entity.supporter;

public enum KofiEventType {
    donation,
    subscription,
    shop_order,
    commission,
    unknown;

    public static KofiEventType fromKofiPayload(String raw) {
        if (raw == null) return unknown;
        return switch (raw.trim().toLowerCase()) {
            case "donation" -> donation;
            case "subscription" -> subscription;
            case "shop order" -> shop_order;
            case "commission" -> commission;
            default -> unknown;
        };
    }
}
