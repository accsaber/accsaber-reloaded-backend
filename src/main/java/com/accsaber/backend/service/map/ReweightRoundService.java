package com.accsaber.backend.service.map;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.projection.ReweightRoundMapRow;
import com.accsaber.backend.model.dto.response.CategoryResponse;
import com.accsaber.backend.model.dto.response.map.ReweightDayResponse;
import com.accsaber.backend.model.entity.map.ReweightRound;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;
import com.accsaber.backend.repository.map.ReweightRoundRepository;
import com.accsaber.backend.service.infra.CategoryService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService.RankedChange;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReweightRoundService {

    private static final String OVERALL_CODE = "overall";
    private static final int MAP_DETAIL_LIMIT = 5;

    private final ReweightRoundRepository roundRepository;
    private final MapDifficultyComplexityRepository complexityRepository;
    private final CategoryService categoryService;

    @Transactional
    public Map<UUID, ReweightRound> open(List<RankedChange> changes) {
        Map<UUID, ReweightRound> rounds = changes.stream()
                .collect(Collectors.groupingBy(c -> c.difficulty().getCategory().getId(), LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), ReweightRoundService::toRound)));
        roundRepository.saveAll(rounds.values());
        return rounds;
    }

    @CacheEvict(value = "reweightRounds", allEntries = true)
    public void evictCache() {
    }

    @Cacheable("reweightRounds")
    public List<ReweightDayResponse> findForCategory(UUID categoryId) {
        CategoryResponse category = categoryService.findById(categoryId);
        List<ReweightRound> rounds = OVERALL_CODE.equals(category.getCode())
                ? roundRepository.findLiveCategoriesOldestFirst()
                : roundRepository.findByCategoryOldestFirst(categoryId);
        Collection<List<ReweightRound>> days = rounds.stream()
                .collect(Collectors.groupingBy(r -> LocalDate.ofInstant(r.getCreatedAt(), ZoneOffset.UTC),
                        LinkedHashMap::new, Collectors.toList()))
                .values();
        Map<UUID, List<ReweightRoundMapRow>> rows = loadMapRows(days);
        return days.stream().map(day -> toResponse(day, rows)).toList();
    }

    private Map<UUID, List<ReweightRoundMapRow>> loadMapRows(Collection<List<ReweightRound>> days) {
        List<UUID> detailedRoundIds = days.stream()
                .filter(day -> mapCount(day) <= MAP_DETAIL_LIMIT)
                .flatMap(day -> day.stream().map(ReweightRound::getId))
                .toList();
        if (detailedRoundIds.isEmpty()) {
            return Map.of();
        }
        return complexityRepository.findMapRowsByRoundIds(detailedRoundIds).stream()
                .collect(Collectors.groupingBy(ReweightRoundMapRow::roundId));
    }

    private static ReweightRound toRound(List<RankedChange> changes) {
        String reason = changes.getFirst().reason();
        boolean sharedReason = true;
        int buffs = 0;
        int nerfs = 0;
        for (RankedChange change : changes) {
            sharedReason &= Objects.equals(reason, change.reason());
            Double from = change.from();
            if (from != null && change.complexity() > from) {
                buffs++;
            } else if (from != null && change.complexity() < from) {
                nerfs++;
            }
        }
        return ReweightRound.builder()
                .category(changes.getFirst().difficulty().getCategory())
                .reason(sharedReason ? reason : null)
                .mapCount(changes.size())
                .buffs(buffs)
                .nerfs(nerfs)
                .build();
    }

    private static ReweightDayResponse toResponse(List<ReweightRound> day, Map<UUID, List<ReweightRoundMapRow>> rows) {
        ReweightRound first = day.getFirst();
        int mapCount = mapCount(day);
        List<String> reasons = day.stream().map(ReweightRound::getReason).distinct().toList();
        return ReweightDayResponse.builder()
                .day(LocalDate.ofInstant(first.getCreatedAt(), ZoneOffset.UTC))
                .at(first.getCreatedAt())
                .categoryCodes(day.stream().map(r -> r.getCategory().getCode()).distinct().toList())
                .reason(reasons.size() == 1 ? reasons.getFirst() : null)
                .mapCount(mapCount)
                .buffs(day.stream().mapToInt(ReweightRound::getBuffs).sum())
                .nerfs(day.stream().mapToInt(ReweightRound::getNerfs).sum())
                .maps(mapCount <= MAP_DETAIL_LIMIT ? mapChanges(day, rows) : null)
                .build();
    }

    private static int mapCount(List<ReweightRound> day) {
        return day.stream().mapToInt(ReweightRound::getMapCount).sum();
    }

    private static List<ReweightDayResponse.MapChange> mapChanges(List<ReweightRound> day,
            Map<UUID, List<ReweightRoundMapRow>> rows) {
        Map<UUID, ReweightRoundMapRow> firsts = new LinkedHashMap<>();
        Map<UUID, Double> lasts = new HashMap<>();
        for (ReweightRound round : day) {
            for (ReweightRoundMapRow row : rows.getOrDefault(round.getId(), List.of())) {
                firsts.putIfAbsent(row.mapDifficultyId(), row);
                lasts.put(row.mapDifficultyId(), row.to());
            }
        }
        return firsts.values().stream()
                .map(row -> ReweightDayResponse.MapChange.builder()
                        .mapId(row.mapId())
                        .mapDifficultyId(row.mapDifficultyId())
                        .songName(row.songName())
                        .difficulty(row.difficulty())
                        .from(row.from())
                        .to(lasts.get(row.mapDifficultyId()))
                        .build())
                .toList();
    }
}
