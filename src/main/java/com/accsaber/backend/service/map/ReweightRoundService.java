package com.accsaber.backend.service.map;

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
import com.accsaber.backend.model.dto.response.map.ReweightRoundResponse;
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
    public List<ReweightRoundResponse> findForCategory(UUID categoryId) {
        CategoryResponse category = categoryService.findById(categoryId);
        List<ReweightRound> rounds = OVERALL_CODE.equals(category.getCode())
                ? roundRepository.findLiveCategoriesOldestFirst()
                : roundRepository.findByCategoryOldestFirst(categoryId);
        Map<UUID, List<ReweightRoundResponse.MapChange>> maps = loadMapChanges(rounds);
        return rounds.stream().map(r -> toResponse(r, maps.get(r.getId()))).toList();
    }

    private Map<UUID, List<ReweightRoundResponse.MapChange>> loadMapChanges(List<ReweightRound> rounds) {
        List<UUID> smallRoundIds = rounds.stream()
                .filter(r -> r.getMapCount() <= MAP_DETAIL_LIMIT)
                .map(ReweightRound::getId)
                .toList();
        if (smallRoundIds.isEmpty()) {
            return Map.of();
        }
        return complexityRepository.findMapRowsByRoundIds(smallRoundIds).stream()
                .collect(Collectors.groupingBy(ReweightRoundMapRow::roundId,
                        Collectors.mapping(ReweightRoundService::toMapChange, Collectors.toList())));
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

    private static ReweightRoundResponse toResponse(ReweightRound round, List<ReweightRoundResponse.MapChange> maps) {
        return ReweightRoundResponse.builder()
                .id(round.getId())
                .at(round.getCreatedAt())
                .categoryCode(round.getCategory().getCode())
                .reason(round.getReason())
                .mapCount(round.getMapCount())
                .buffs(round.getBuffs())
                .nerfs(round.getNerfs())
                .maps(round.getMapCount() <= MAP_DETAIL_LIMIT ? Objects.requireNonNullElse(maps, List.of()) : null)
                .build();
    }

    private static ReweightRoundResponse.MapChange toMapChange(ReweightRoundMapRow row) {
        return ReweightRoundResponse.MapChange.builder()
                .mapId(row.mapId())
                .mapDifficultyId(row.mapDifficultyId())
                .songName(row.songName())
                .difficulty(row.difficulty())
                .from(row.from())
                .to(row.to())
                .build();
    }
}
