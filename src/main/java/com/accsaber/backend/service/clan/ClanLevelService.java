package com.accsaber.backend.service.clan;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.IntPredicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.clan.ClanLevelResponse;
import com.accsaber.backend.model.dto.response.clan.ClanLevelStepResponse;
import com.accsaber.backend.model.dto.response.clan.ClanUnlocksResponse;
import com.accsaber.backend.model.dto.response.clan.ClanXpGrantResponse;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanLevelCapacity;
import com.accsaber.backend.model.entity.clan.ClanLevelItem;
import com.accsaber.backend.model.entity.clan.ClanLevelWarMode;
import com.accsaber.backend.model.entity.clan.ClanWarModeAxis;
import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.clan.ClanItemRepository;
import com.accsaber.backend.repository.clan.ClanLevelCapacityRepository;
import com.accsaber.backend.repository.clan.ClanLevelItemRepository;
import com.accsaber.backend.repository.clan.ClanLevelWarModeRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.clan.ClanXpGrantRepository;
import com.accsaber.backend.service.item.ItemMapper;
import com.accsaber.backend.util.LevelCurve;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanLevelService {

    private static final UUID CLAN_LEVEL_CURVE_ID = UUID.fromString("acc00000-0000-0000-0000-000000000030");
    private static final int LEVEL_COST_CAP = 100;

    private final CurveRepository curveRepository;
    private final ClanRepository clanRepository;
    private final ClanXpGrantRepository grantRepository;
    private final ClanItemRepository clanItemRepository;
    private final ClanLevelCapacityRepository capacityRepository;
    private final ClanLevelWarModeRepository warModeRepository;
    private final ClanLevelItemRepository levelItemRepository;
    private final ClanProperties clanProperties;

    private volatile LevelCurve cachedCurve;

    public record CapacityTable(List<ClanLevelCapacity> rows, int maxMembers) {

        public int at(int level, ClanCapacity capacity) {
            int total = rows.stream()
                    .filter(row -> row.getCapacity() == capacity && row.getLevel() <= level)
                    .mapToInt(ClanLevelCapacity::getAmount)
                    .sum();
            return capacity == ClanCapacity.member_slots ? Math.min(total, maxMembers) : total;
        }
    }

    public LevelResponse levelOf(Clan clan) {
        return LevelResponse.of(curve().progressAt(clan.getTotalXp()), clan.getTotalXp(), null);
    }

    public CapacityTable capacities() {
        return new CapacityTable(capacityRepository.findAll(), clanProperties.getMaxMembers());
    }

    public int capacityOf(Clan clan, ClanCapacity capacity) {
        return capacities().at(levelOf(clan).getLevel(), capacity);
    }

    public boolean hasWarMode(Clan clan, ClanWarModeAxis axis, String mode) {
        return warModeRepository.findById(new ClanLevelWarMode.Key(axis, mode))
                .map(row -> row.getLevel() <= levelOf(clan).getLevel())
                .orElse(false);
    }

    public double rosterFactor(Clan clan) {
        return Math.max(1.0, clan.getRosterStrength() / clanProperties.getRosterReferenceStrength());
    }

    @Transactional
    public boolean grantXp(UUID clanId, ClanXpAward award) {
        Clan clan = clanRepository.findByIdAndActiveTrueForUpdate(clanId).orElse(null);
        if (clan == null) {
            return false;
        }
        double factor = award.rosterScaled() ? rosterFactor(clan) : 1.0;
        double amount = award.rawAmount() / factor;
        if (grantRepository.insertIfAbsent(clanId, award.source().name(), award.sourceId(), award.rawAmount(),
                factor, amount) == 0) {
            return false;
        }
        int fromLevel = levelOf(clan).getLevel();
        clan.setTotalXp(clan.getTotalXp() + amount);
        clanRepository.saveAndFlush(clan);
        int toLevel = levelOf(clan).getLevel();
        if (toLevel > fromLevel) {
            clanItemRepository.grantLevelItems(clanId, fromLevel, toLevel);
        }
        return true;
    }

    @Transactional
    public void grantStartingItems(UUID clanId) {
        clanItemRepository.grantLevelItems(clanId, -1, 0);
    }

    public ClanLevelResponse level(UUID clanId) {
        Clan clan = clanRepository.findByIdAndActiveTrue(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan", clanId));
        LevelResponse progress = levelOf(clan);
        return new ClanLevelResponse(progress, unlocks(level -> level <= progress.getLevel()));
    }

    public List<ClanLevelStepResponse> table() {
        UnlockRows rows = loadUnlockRows();
        TreeSet<Integer> levels = new TreeSet<>();
        rows.capacities().forEach(row -> levels.add(row.getLevel()));
        rows.warModes().forEach(row -> levels.add(row.getLevel()));
        rows.items().forEach(row -> levels.add(row.getLevel()));
        LevelCurve curve = curve();
        return levels.stream()
                .map(level -> new ClanLevelStepResponse(level, curve.cumulativeXpForLevel(level),
                        rows.unlocks(candidate -> candidate == level)))
                .toList();
    }

    public Page<ClanXpGrantResponse> xpHistory(UUID clanId, Pageable pageable) {
        return grantRepository.findByClan_IdOrderByCreatedAtDesc(clanId, pageable).map(ClanXpGrantResponse::of);
    }

    private ClanUnlocksResponse unlocks(IntPredicate levelFilter) {
        return loadUnlockRows().unlocks(levelFilter);
    }

    private UnlockRows loadUnlockRows() {
        return new UnlockRows(capacityRepository.findAll(), warModeRepository.findAll(),
                levelItemRepository.findAllWithItems(), clanProperties.getMaxMembers());
    }

    private record UnlockRows(List<ClanLevelCapacity> capacities, List<ClanLevelWarMode> warModes,
            List<ClanLevelItem> items, int maxMembers) {

        ClanUnlocksResponse unlocks(IntPredicate levelFilter) {
            Map<ClanCapacity, Integer> totals = new EnumMap<>(ClanCapacity.class);
            capacities.stream().filter(row -> levelFilter.test(row.getLevel()))
                    .forEach(row -> totals.merge(row.getCapacity(), row.getAmount(), Integer::sum));
            totals.computeIfPresent(ClanCapacity.member_slots, (capacity, amount) -> Math.min(amount, maxMembers));
            List<ClanArena> arenas = warModes.stream()
                    .filter(row -> row.getAxis() == ClanWarModeAxis.arena && levelFilter.test(row.getLevel()))
                    .map(row -> ClanArena.valueOf(row.getMode())).toList();
            List<ClanRuleset> rulesets = warModes.stream()
                    .filter(row -> row.getAxis() == ClanWarModeAxis.ruleset && levelFilter.test(row.getLevel()))
                    .map(row -> ClanRuleset.valueOf(row.getMode())).toList();
            return new ClanUnlocksResponse(totals, arenas, rulesets, items.stream()
                    .filter(row -> levelFilter.test(row.getLevel()))
                    .map(row -> ItemMapper.toItemResponse(row.getItem())).toList());
        }
    }

    private LevelCurve curve() {
        LevelCurve curve = cachedCurve;
        if (curve == null) {
            Curve stored = curveRepository.findById(CLAN_LEVEL_CURVE_ID)
                    .orElseThrow(() -> new IllegalStateException("Clan level curve not found"));
            curve = new LevelCurve(stored.getXParameterValue(), stored.getYParameterValue(), LEVEL_COST_CAP);
            cachedCurve = curve;
        }
        return curve;
    }
}
