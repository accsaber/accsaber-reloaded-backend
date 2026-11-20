package com.accsaber.backend.service.stats;

import java.util.Collection;
import java.util.Map;

public record SiteStatsFilter(String country, Collection<Long> userIds) {

    public SiteStatsFilter {
        country = StatsQueryRunner.normalizeCountry(country);
    }

    boolean matchesNobody() {
        return userIds != null && userIds.isEmpty();
    }

    String playerClause(String userAlias, Map<String, Object> params) {
        String sql = StatsQueryRunner.countryClause(userAlias + "country", country, params);
        if (userIds == null) {
            return sql;
        }
        if (userIds.isEmpty()) {
            return sql + " AND FALSE";
        }
        params.put("userIds", userIds);
        return sql + " AND " + userAlias + "id IN (:userIds)";
    }
}
