package com.accsaber.backend.model.dto.response.clan;

public record ClanWarStrikeRulesResponse(
        double guard,
        double baseDamage,
        double escalation,
        double disparityExponent,
        double missingScoreMultiplier,
        double chipDamage,
        double chipWindow,
        double chipSkillExponent,
        double minChipDamage,
        double missingScoreEscalation,
        double chipEscalation,
        long brokenDurationHours,
        long wipeDurationHours) {
}
