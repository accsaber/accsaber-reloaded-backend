package com.accsaber.backend.service.campaign;

import java.util.Set;
import java.util.function.LongSupplier;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.CampaignBound;

final class CampaignEditRules {

    private CampaignEditRules() {
    }

    static void assertUnderCap(CampaignEditor editor, LongSupplier current, int cap, String what) {
        if (editor.privileged()) {
            return;
        }
        if (current.getAsLong() >= cap) {
            throw new ValidationException("Campaign has reached the maximum of " + cap + " " + what);
        }
    }

    static Double requireNonNegativeXp(Double xp) {
        if (Math.signum(xp) < 0) {
            throw new ValidationException("xp", "must be non-negative");
        }
        return xp;
    }

    static void validateBounds(String field, boolean lowerBetter, Double bound, Double cap) {
        if (bound == null && cap == null) {
            throw new ValidationException(field, "a requirement value is required");
        }
        if (!lowerBetter && bound != null && cap != null && bound.compareTo(cap) > 0) {
            throw new ValidationException(field, "the lower bound cannot exceed the upper bound");
        }
    }

    static boolean clears(Set<CampaignBound> clear, CampaignBound bound) {
        return clear != null && clear.contains(bound);
    }

    static void assertNoClearConflict(Set<CampaignBound> clear, String field,
            Double value, Double valueMax) {
        if (value != null && clears(clear, CampaignBound.VALUE)) {
            throw new ValidationException(field, "cannot be set and cleared in the same request");
        }
        if (valueMax != null && clears(clear, CampaignBound.VALUE_MAX)) {
            throw new ValidationException(field + "Max", "cannot be set and cleared in the same request");
        }
    }

    static boolean boundChanged(Double current, Double replacement) {
        if (current == null || replacement == null) {
            return current != replacement;
        }
        return current.compareTo(replacement) != 0;
    }
}
