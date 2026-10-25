package com.accsaber.backend.model.dto.response.clan;

public record ClanWarStrikeRulesResponse(
        double guard,
        double baseDamage,
        double escalation,
        double disparityExponent,
        double chipDamage,
        double chipWindow,
        double chipSkillExponent,
        double chipEscalation,
        double repeatMapShare,
        double mapBreakDecay,
        double mapBreakFloor,
        double lastStandDamage,
        double lastStandFrom,
        long brokenDurationHours,
        long wipeDurationHours) {
}
