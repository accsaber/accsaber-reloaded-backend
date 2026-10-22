package com.accsaber.backend.model.dto.response.clan;

public record ClanWarStrikePreviewResponse(
        double damage,
        int hitsToBreak,
        long hitsThisCycle,
        long noScoreHitsThisCycle,
        long chipsThisCycle,
        double attackerSkill,
        double victimSkill,
        double disparity,
        ClanWarStrikeMapResponse onMap) {
}
