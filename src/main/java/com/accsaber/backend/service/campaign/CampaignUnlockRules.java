package com.accsaber.backend.service.campaign;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.UserCampaign;

final class CampaignUnlockRules {

    record Window(Instant at, boolean exclusive) {
    }

    private CampaignUnlockRules() {
    }

    static Instant since(UserCampaign uc) {
        if (uc == null || uc.getCampaign().isLegacy()) {
            return Instant.EPOCH;
        }
        if (uc.getStartedAt() != null) {
            return uc.getStartedAt();
        }
        return uc.getCreatedAt() != null ? uc.getCreatedAt() : Instant.EPOCH;
    }

    static Window window(List<UUID> prereqs, CampaignPrerequisiteMode mode, Map<UUID, Instant> completionTimes,
            Instant since, boolean agnostic) {
        if (agnostic || prereqs.isEmpty()) {
            return new Window(since, false);
        }
        if (mode == CampaignPrerequisiteMode.AND) {
            Instant latest = null;
            for (UUID p : prereqs) {
                Instant t = completionTimes.get(p);
                if (t == null) {
                    return null;
                }
                if (latest == null || t.isAfter(latest)) {
                    latest = t;
                }
            }
            return new Window(latest, true);
        }
        Instant earliest = null;
        for (UUID p : prereqs) {
            Instant t = completionTimes.get(p);
            if (t != null && (earliest == null || t.isBefore(earliest))) {
                earliest = t;
            }
        }
        return earliest != null ? new Window(earliest, true) : null;
    }

    static boolean counts(Instant scoreTime, Window window) {
        if (window == null || scoreTime == null) {
            return false;
        }
        return window.exclusive() ? scoreTime.isAfter(window.at()) : !scoreTime.isBefore(window.at());
    }

    static boolean prereqsCompleted(List<UUID> prereqs, CampaignPrerequisiteMode mode, Set<UUID> completedIds) {
        if (prereqs.isEmpty()) {
            return true;
        }
        if (mode == CampaignPrerequisiteMode.AND) {
            return completedIds.containsAll(prereqs);
        }
        for (UUID p : prereqs) {
            if (completedIds.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
