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
        double missingScoreEscalation,
        double chipEscalation,
        double mapBreakDecay,
        double mapBreakFloor,
        double lastStandDamage,
        double lastStandFrom,
        long brokenDurationHours,
        long wipeDurationHours) {
}
