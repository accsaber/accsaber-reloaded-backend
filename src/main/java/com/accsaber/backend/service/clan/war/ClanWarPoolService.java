package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.ClanArenaSpec;
import com.accsaber.backend.model.dto.request.clan.DeclareClanWarRequest;
import com.accsaber.backend.model.dto.response.clan.ClanWarPoolEntryResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.score.MyScoreSummary;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarPoolEntry;
import com.accsaber.backend.model.entity.clan.war.ClanWarPoolSource;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.clan.war.ClanWarPoolEntryRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.clan.ClanAccessService;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanPermission;
import com.accsaber.backend.service.clan.ClanRefCache;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.util.CampaignScoreMetrics;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanWarPoolService {

    private static final String OVERALL_CODE = "overall";

    private final ClanWarRepository warRepository;
    private final ClanWarSideRepository sideRepository;
    private final ClanWarPoolEntryRepository poolRepository;
    private final CategoryRepository categoryRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final ClanAccessService accessService;
    private final ClanCosmeticService cosmeticService;
    private final MapService mapService;
    private final ScoreRepository scoreRepository;
    private final ClanWarRosterService rosterService;
    private final ClanProperties clanProperties;

    public record PlaylistSource(String title, List<MapDifficulty> difficulties) {
    }

    public ClanArenaSpec spec(DeclareClanWarRequest request, double attackerStanding, double defenderStanding) {
        ClanArena arena = request.getArena();
        assertArenaTargets(request);
        int poolSize = clanProperties.getWar().getPoolSize();
        if (arena == ClanArena.random) {
            return new ClanArenaSpec(null, null, null, poolSize, 0, 0);
        }
        double underdog = underdogShare(attackerStanding, defenderStanding, clanProperties.getWar().getMaxUnderdogShare());
        double attackerShare = attackerStanding < defenderStanding ? underdog : 1.0 - underdog;
        int attackerPicks = (int) Math.round(poolSize * attackerShare);
        return new ClanArenaSpec(request.getCategoryId(), request.getComplexityMin(), request.getComplexityMax(),
                poolSize, attackerPicks, poolSize - attackerPicks);
    }

    static double underdogShare(double attackerStanding, double defenderStanding, double maxShare) {
        double stronger = Math.max(attackerStanding, defenderStanding);
        double ratio = stronger > 0 ? Math.min(attackerStanding, defenderStanding) / stronger : 1.0;
        return 0.5 + (maxShare - 0.5) * (1.0 - ratio);
    }

    @Transactional
    public void seed(ClanWar war, List<UUID> attackerPicks) {
        ClanArenaSpec spec = war.getArenaSpec();
        if (war.getArena() == ClanArena.random) {
            if (!attackerPicks.isEmpty()) {
                throw new ValidationException("mapDifficultyIds", "a random arena takes no picks");
            }
            fillRandom(war, null, ClanWarPoolSource.random, spec.poolSize());
            if (war.getRuleset() == ClanRuleset.duel) {
                lock(war, false);
            }
            return;
        }
        savePicks(war, war.getAttackerClan(), legalPicks(spec, attackerPicks, spec.attackerPicks()));
    }

    @Transactional
    public void submitPicks(UUID warId, Long playerId, List<UUID> picks, boolean berserker) {
        User actor = accessService.player(playerId);
        ClanWar war = warRepository.lockOrThrow(warId);
        Clan defender = war.getDefenderClan();
        accessService.require(defender.getId(), actor.getId(), ClanPermission.SUBMIT_PICKS);
        ClanWarSide side = sideRepository.findByWar_IdAndClan_Id(warId, defender.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ClanWarSide", warId));
        if (war.getStatus() != ClanWarStatus.picking || side.getPicksSubmittedAt() != null) {
            throw new ConflictException("This war is no longer taking picks");
        }
        if (berserker && war.getRuleset() != ClanRuleset.berserker) {
            throw new ValidationException("berserker", "the attacker did not offer Berserker");
        }
        List<UUID> legal = legalPicks(war.getArenaSpec(), picks, war.getArenaSpec().defenderPicks());
        Set<UUID> taken = new HashSet<>(poolRepository.findByWarId(warId).stream()
                .map(entry -> entry.getMapDifficulty().getId()).toList());
        List<UUID> fresh = legal.stream().filter(id -> !taken.contains(id)).toList();
        savePicks(war, defender, fresh);
        fillRandom(war, defender.getId(), ClanWarPoolSource.replacement, legal.size() - fresh.size());
        side.setPicksSubmittedAt(Instant.now());
        if (side.getLeadUser() == null) {
            side.setLeadUser(actor);
        }
        lock(war, berserker);
    }

    @Transactional
    public void closePicks(UUID warId) {
        ClanWar war = warRepository.findByIdForUpdate(warId).orElse(null);
        if (war == null || war.getStatus() != ClanWarStatus.picking || war.getPicksDueAt().isAfter(Instant.now())) {
            return;
        }
        fillRandom(war, null, ClanWarPoolSource.random, war.getArenaSpec().defenderPicks());
        lock(war, false);
    }

    public List<ClanWarPoolEntryResponse> pool(ClanWar war, UUID viewerClanId, Long viewerId) {
        List<ClanWarPoolEntry> entries = poolRepository.findByWarId(war.getId()).stream()
                .filter(entry -> war.getStatus() != ClanWarStatus.picking
                        || entry.getSource() == ClanWarPoolSource.pick
                        || (entry.getPickedByClan() != null && entry.getPickedByClan().getId().equals(viewerClanId)))
                .toList();
        List<UUID> difficultyIds = entries.stream().map(entry -> entry.getMapDifficulty().getId()).toList();
        Map<UUID, PublicMapDifficultyResponse> difficulties = mapService.getDifficultyResponsesPublic(difficultyIds);
        Map<UUID, PublicClanResponse> clans = cosmeticService.publicRefs(war.leads());
        Map<UUID, ClanWarRosterService.Pairing> pairings = rosterService.pairings(war, viewerId, difficultyIds);
        Map<UUID, Map<Long, Score>> scores = scores(viewerId, pairings.values(), difficultyIds);
        return entries.stream().map(entry -> {
            UUID difficultyId = entry.getMapDifficulty().getId();
            Map<Long, Score> onMap = scores.getOrDefault(difficultyId, Map.of());
            ClanWarRosterService.Pairing pairing = pairings.get(difficultyId);
            User target = pairing == null ? null : pairing.target();
            return new ClanWarPoolEntryResponse(difficulties.get(difficultyId),
                    entry.getPickedByClan() == null ? null : clans.get(entry.getPickedByClan().getId()),
                    entry.getSource(), summary(viewerId == null ? null : onMap.get(viewerId)),
                    target == null ? null : ClanRefCache.playerRef(target),
                    summary(target == null ? null : onMap.get(target.getId())), pairing != null && pairing.picked());
        }).toList();
    }

    private Map<UUID, Map<Long, Score>> scores(Long viewerId, Collection<ClanWarRosterService.Pairing> pairings,
            List<UUID> difficultyIds) {
        Set<Long> userIds = pairings.stream().map(pairing -> pairing.target().getId())
                .collect(Collectors.toCollection(HashSet::new));
        if (viewerId != null) {
            userIds.add(viewerId);
        }
        if (userIds.isEmpty() || difficultyIds.isEmpty()) {
            return Map.of();
        }
        return scoreRepository.findActiveByUsersAndMapDifficultyIdIn(userIds, difficultyIds).stream()
                .collect(Collectors.groupingBy(score -> score.getMapDifficulty().getId(),
                        Collectors.toMap(score -> score.getUser().getId(), Function.identity(), (first, second) -> first)));
    }

    private static MyScoreSummary summary(Score score) {
        return score == null ? null : MyScoreSummary.builder()
                .id(score.getId())
                .score(score.getScore())
                .accuracy(CampaignScoreMetrics.accuracy(score))
                .ap(score.getAp())
                .weightedAp(score.getWeightedAp())
                .rank(score.getRank())
                .timeSet(score.getTimeSet())
                .build();
    }

    public PlaylistSource playlistSource(UUID warId) {
        ClanWar war = warRepository.findWithRefsById(warId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanWar", warId));
        if (war.getStatus() == ClanWarStatus.picking) {
            throw new ValidationException("The pool is still being picked");
        }
        return new PlaylistSource("AccSaber War: " + war.getAttackerClan().getTag() + " vs "
                + war.getDefenderClan().getTag(), poolRepository.findDifficulties(warId));
    }

    private void assertArenaTargets(DeclareClanWarRequest request) {
        boolean category = request.getCategoryId() != null;
        boolean complexity = request.getComplexityMin() != null || request.getComplexityMax() != null;
        switch (request.getArena()) {
            case category_turf -> {
                if (!category || complexity) {
                    throw new ValidationException("categoryId", "a category turf war needs a category and nothing else");
                }
                Category turf = categoryRepository.requireActive(request.getCategoryId());
                if (OVERALL_CODE.equals(turf.getCode())) {
                    throw new ValidationException("categoryId", "must be a ranked category, not overall");
                }
            }
            case complexity_turf -> {
                if (category || request.getComplexityMin() == null || request.getComplexityMax() == null
                        || request.getComplexityMin() >= request.getComplexityMax()) {
                    throw new ValidationException("complexityMin", "a complexity turf war needs a range and nothing else");
                }
            }
            case mixed, random -> {
                if (category || complexity) {
                    throw new ValidationException("arena", "only turf wars take a category or a complexity range");
                }
            }
        }
    }

    private List<UUID> legalPicks(ClanArenaSpec spec, List<UUID> picks, int required) {
        Set<UUID> distinct = new HashSet<>(picks);
        if (distinct.size() != picks.size() || picks.size() != required) {
            throw new ValidationException("mapDifficultyIds", "must be " + required + " different difficulties");
        }
        if (picks.isEmpty()) {
            return picks;
        }
        if (poolRepository.findLegal(distinct, spec.categoryId(), spec.complexityMin(), spec.complexityMax()).size()
                != required) {
            throw new ValidationException("mapDifficultyIds", "every pick has to be a ranked map legal in this arena");
        }
        return picks;
    }

    private void savePicks(ClanWar war, Clan picker, List<UUID> picks) {
        poolRepository.saveAll(picks.stream()
                .map(id -> ClanWarPoolEntry.builder()
                        .war(war)
                        .mapDifficulty(mapDifficultyRepository.getReferenceById(id))
                        .pickedByClan(picker)
                        .source(ClanWarPoolSource.pick)
                        .build())
                .toList());
        poolRepository.flush();
    }

    private void fillRandom(ClanWar war, UUID pickedBy, ClanWarPoolSource source, int count) {
        if (count <= 0) {
            return;
        }
        ClanArenaSpec spec = war.getArenaSpec();
        poolRepository.insertRandom(war.getId(), pickedBy, source.name(), count, spec.categoryId(),
                spec.complexityMin(), spec.complexityMax());
    }

    private void lock(ClanWar war, boolean berserkerAccepted) {
        if (!berserkerAccepted) {
            war.setRuleset(ClanRuleset.duel);
        }
        rosterService.prepare(war);
    }
}
