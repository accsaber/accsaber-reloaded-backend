package com.accsaber.backend.service.mission;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import org.springframework.stereotype.Component;

import com.accsaber.backend.model.entity.mission.MissionPool;

@Component
public class MissionRolloverService {

    public Instant nextRollover(MissionPool pool, Instant now) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime nowLocal = now.atZone(zone).toLocalDateTime();
        LocalDateTime todayFour = nowLocal.toLocalDate().atTime(4, 0);
        LocalDateTime nextDaily = nowLocal.isBefore(todayFour) ? todayFour : todayFour.plusDays(1);

        return switch (pool) {
            case daily -> nextDaily.atZone(zone).toInstant();
            case weekly -> nextDaily
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                    .atZone(zone).toInstant();
            case event -> nextDaily.plusYears(10).atZone(zone).toInstant();
        };
    }

    public long deterministicSeed(Long userId, LocalDate date, MissionPool pool) {
        long base = (long) date.getYear() * 1000 + date.getDayOfYear();
        return (userId ^ Long.rotateLeft(base, 17)) * 0x9E3779B97F4A7C15L
                ^ pool.ordinal();
    }
}
