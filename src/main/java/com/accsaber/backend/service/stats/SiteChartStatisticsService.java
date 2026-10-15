package com.accsaber.backend.service.stats;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.statistics.DistributionEntryResponse;
import com.accsaber.backend.model.dto.response.statistics.TimeSeriesPointResponse;
import com.accsaber.backend.util.HmdMapper;
import com.accsaber.backend.util.TimeRangeUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteChartStatisticsService {

    private final StatsQueryRunner queryRunner;

    @Cacheable(value = "statistics", key = "'newplayersperday:' + #amount + ':' + #unit + ':' + #country")
    public List<TimeSeriesPointResponse> getNewPlayersPerDay(int amount, String unit, String country) {
        return timeSeries(amount, unit, (trunc, params) -> SiteStatisticsQueries.newPlayersPerDay(trunc, country, params));
    }

    @Cacheable(value = "statistics", key = "'scoresperday:' + #amount + ':' + #unit + ':' + #country")
    public List<TimeSeriesPointResponse> getScoresPerDay(int amount, String unit, String country) {
        return timeSeries(amount, unit, (trunc, params) -> SiteStatisticsQueries.scoresPerDay(trunc, country, params));
    }

    @Cacheable(value = "statistics", key = "'cumulativeaccounts:' + #amount + ':' + #unit + ':' + #country")
    public List<TimeSeriesPointResponse> getCumulativeAccounts(int amount, String unit, String country) {
        return timeSeries(amount, unit,
                (trunc, params) -> SiteStatisticsQueries.cumulativeAccounts(trunc, country, params));
    }

    @Cacheable(value = "statistics", key = "'cumulativescores:' + #amount + ':' + #unit + ':' + #country")
    public List<TimeSeriesPointResponse> getCumulativeScores(int amount, String unit, String country) {
        return timeSeries(amount, unit, (trunc, params) -> SiteStatisticsQueries.cumulativeScores(trunc, country, params));
    }

    @Cacheable(value = "statistics", key = "'scorespercategory:' + #country")
    public List<DistributionEntryResponse> getScoresPerCategory(String country) {
        Map<String, Object> params = new LinkedHashMap<>();
        return queryRunner.distribution(SiteStatisticsQueries.scoresPerCategory(country, params), params);
    }

    @Cacheable(value = "statistics", key = "'playersbyhmd:' + #country")
    public List<DistributionEntryResponse> getPlayersByHmd(String country) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<DistributionEntryResponse> rows = queryRunner.distribution(
                SiteStatisticsQueries.playersByHmd(country, params), params);

        Map<String, Long> aggregated = new LinkedHashMap<>();
        for (DistributionEntryResponse row : rows) {
            String label = HmdMapper.normalize(row.getLabel());
            if (label == null)
                continue;
            aggregated.merge(label, row.getCount(), Long::sum);
        }

        return aggregated.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> DistributionEntryResponse.builder()
                        .label(e.getKey())
                        .count(e.getValue())
                        .build())
                .toList();
    }

    @Cacheable(value = "statistics", key = "'playerspercountry'")
    public List<DistributionEntryResponse> getPlayersPerCountry() {
        return queryRunner.distribution(SiteStatisticsQueries.PLAYERS_PER_COUNTRY, Map.of());
    }

    private List<TimeSeriesPointResponse> timeSeries(int amount, String unit,
            BiFunction<String, Map<String, Object>, String> query) {
        Instant since = TimeRangeUtil.computeSince(amount, unit);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("since", since);
        return queryRunner.timeSeries(query.apply(TimeRangeUtil.granularity(since), params), params);
    }
}
