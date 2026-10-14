package com.accsaber.backend.service.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.dto.response.statistics.DistributionEntryResponse;
import com.accsaber.backend.model.dto.response.statistics.TimeSeriesPointResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StatsQueryRunner {

    private final EntityManager entityManager;

    public static String normalizeCountry(String country) {
        if (country == null)
            return null;
        String trimmed = country.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String countryClause(String countryColumn, String country, Map<String, Object> params) {
        String normalized = normalizeCountry(country);
        if (normalized == null) {
            return "";
        }
        params.put("country", normalized);
        return " AND LOWER(" + countryColumn + ") = LOWER(:country)";
    }

    public static String countryExists(String userIdColumn, String country, Map<String, Object> params) {
        String clause = countryClause("cu.country", country, params);
        return clause.isEmpty() ? "" : " AND EXISTS (SELECT 1 FROM users cu WHERE cu.id = " + userIdColumn + clause + ")";
    }

    public static long num(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    public static Double dbl(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    public static Double rate(long part, long whole) {
        return whole <= 0 ? null : (double) part / whole;
    }

    @SuppressWarnings("unchecked")
    public <T> Page<T> paged(String sql, Map<String, Object> params, Pageable pageable,
            Function<Object[], T> mapper) {

        String countSql = "SELECT COUNT(*) FROM (" + sql + ") _count";
        Query countQuery = entityManager.createNativeQuery(countSql);
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Query dataQuery = entityManager.createNativeQuery(sql);
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<Object[]> rows = dataQuery.getResultList();
        List<T> content = rows.stream().map(mapper).toList();

        return new PageImpl<>(content, pageable, total);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> list(String sql, Map<String, Object> params, Function<Object[], T> mapper) {
        Query query = entityManager.createNativeQuery(sql);
        params.forEach(query::setParameter);
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(mapper).toList();
    }

    public List<TimeSeriesPointResponse> timeSeries(String sql, Map<String, Object> params) {
        Query query = entityManager.createNativeQuery(sql);
        params.forEach(query::setParameter);
        return mapTimeSeries(query);
    }

    public List<DistributionEntryResponse> distribution(String sql, Map<String, Object> params) {
        return list(sql, params, row -> DistributionEntryResponse.builder()
                .label((String) row[0])
                .count(((Number) row[1]).longValue())
                .build());
    }

    @SuppressWarnings("unchecked")
    private List<TimeSeriesPointResponse> mapTimeSeries(Query query) {
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> TimeSeriesPointResponse.builder()
                        .date((LocalDate) row[0])
                        .value(((Number) row[1]).longValue())
                        .build())
                .toList();
    }
}
