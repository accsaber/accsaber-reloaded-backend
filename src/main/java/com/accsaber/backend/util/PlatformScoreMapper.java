package com.accsaber.backend.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreStats;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;

public final class PlatformScoreMapper {

    public static final Set<String> BANNED_MODIFIER_CODES = Set.of("NO", "NB", "SS", "SC", "SN");

    private PlatformScoreMapper() {
    }

    public static boolean hasBannedModifier(String modifiersStr) {
        if (modifiersStr == null || modifiersStr.isBlank())
            return false;
        for (String code : modifiersStr.split(",")) {
            if (BANNED_MODIFIER_CODES.contains(code.trim()))
                return true;
        }
        return false;
    }

    public static boolean hasBannedModifier(Collection<String> mods) {
        if (mods == null || mods.isEmpty())
            return false;
        for (String code : mods) {
            if (code != null && BANNED_MODIFIER_CODES.contains(code.trim()))
                return true;
        }
        return false;
    }

    public static SubmitScoreRequest fromBeatLeader(BeatLeaderScoreResponse bl, UUID mapDifficultyId,
            Long userId, Map<String, UUID> modifierCodeToId) {
        SubmitScoreRequest request = new SubmitScoreRequest();
        request.setUserId(userId);
        request.setMapDifficultyId(mapDifficultyId);
        request.setScore(bl.getBaseScore());
        request.setScoreNoMods(bl.getBaseScore());
        request.setRank(bl.getRank());
        request.setRankWhenSet(bl.getRank());
        request.setBlScoreId(bl.getId());
        request.setMaxCombo(bl.getMaxCombo() != null && bl.getMaxCombo() > 0 ? bl.getMaxCombo() : null);
        request.setBadCuts(bl.getBadCuts());
        request.setMisses(bl.getMissedNotes());
        request.setWallHits(bl.getWallsHit());
        request.setBombHits(bl.getBombCuts());
        request.setPauses(bl.getPauses());
        request.setStreak115(bl.getMaxStreak());
        request.setPlayCount(bl.getPlayCount() != null && bl.getPlayCount() > 0 ? bl.getPlayCount() : null);
        request.setHmd(HmdMapper.fromBeatLeaderId(bl.getHmd()));
        request.setTimeSet(bl.getTimepost() != null && bl.getTimepost() > 0
                ? Instant.ofEpochSecond(bl.getTimepost())
                : null);
        request.setModifierIds(resolveModifiers(bl.getModifiers(), modifierCodeToId));
        return request;
    }

    public static SubmitScoreRequest fromScoreSaber(ScoreSaberScoreResponse ss, ScoreSaberScoreStats stats,
            UUID mapDifficultyId, Long userId, Map<String, UUID> modifierCodeToId) {
        SubmitScoreRequest request = new SubmitScoreRequest();
        request.setUserId(userId);
        request.setMapDifficultyId(mapDifficultyId);
        request.setScore(ss.getModifiedScore());
        request.setScoreNoMods(ss.getUnmodifiedScore());
        request.setRank(ss.getRank());
        request.setRankWhenSet(ss.getRank());
        request.setSsScoreId(ss.getId());
        request.setMaxCombo(ss.getMaxCombo() != null && ss.getMaxCombo() > 0 ? ss.getMaxCombo() : null);
        request.setBadCuts(ss.getBadCuts());
        request.setMisses(ss.getMissedNotes());
        request.setHmd(resolveHmd(ss));
        request.setTimeSet(ss.getCreatedAt() != null ? Instant.parse(ss.getCreatedAt()) : null);
        request.setModifierIds(resolveModifiers(ss.getMods(), modifierCodeToId));
        if (stats != null) {
            request.setStreak115(stats.getMax115Streak());
            request.setBombHits(sumBombs(stats));
        }
        return request;
    }

    private static String resolveHmd(ScoreSaberScoreResponse ss) {
        if (ss.getDevice() != null && ss.getDevice().getHmd() != null && !ss.getDevice().getHmd().isBlank()) {
            return ss.getDevice().getHmd();
        }
        return HmdMapper.fromBeatLeaderId(ss.getLegacyHmdId());
    }

    private static Integer sumBombs(ScoreSaberScoreStats stats) {
        Integer left = stats.getLeftBombs();
        Integer right = stats.getRightBombs();
        if (left == null && right == null)
            return null;
        return (left == null ? 0 : left) + (right == null ? 0 : right);
    }

    private static List<UUID> resolveModifiers(String modifiersStr, Map<String, UUID> modifierCodeToId) {
        if (modifiersStr == null || modifiersStr.isBlank()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (String code : modifiersStr.split(",")) {
            String trimmed = code.trim();
            if (!trimmed.isEmpty()) {
                UUID id = modifierCodeToId.get(trimmed);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static List<UUID> resolveModifiers(Collection<String> mods, Map<String, UUID> modifierCodeToId) {
        if (mods == null || mods.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (String code : mods) {
            if (code == null)
                continue;
            String trimmed = code.trim();
            if (!trimmed.isEmpty()) {
                UUID id = modifierCodeToId.get(trimmed);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
