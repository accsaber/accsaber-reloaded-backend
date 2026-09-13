package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.clan.ClanSeasonResponse;
import com.accsaber.backend.model.dto.response.clan.ClanStandingResponse;
import com.accsaber.backend.model.entity.clan.ClanItemSource;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.clan.ClanSeasonResult;
import com.accsaber.backend.model.entity.clan.ClanSeasonReward;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.repository.clan.ClanItemRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.clan.ClanSeasonRepository;
import com.accsaber.backend.repository.clan.ClanSeasonResultRepository;
import com.accsaber.backend.repository.clan.ClanSeasonRewardRepository;
import com.accsaber.backend.repository.clan.ClanSeasonStandingRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.service.item.ItemService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanSeasonService {

    private final ClanSeasonRepository seasonRepository;
    private final ClanSeasonResultRepository resultRepository;
    private final ClanSeasonRewardRepository rewardRepository;
    private final ClanRepository clanRepository;
    private final ClanItemRepository clanItemRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanStandingService standingService;
    private final ItemService itemService;
    private final ClanProperties clanProperties;

    public Page<ClanSeasonResponse> list(Pageable pageable) {
        return seasonRepository.findAllByOrderByStartsAtDesc(pageable).map(ClanSeasonResponse::of);
    }

    public ClanSeasonResponse get(String slugOrId) {
        return ClanSeasonResponse.of(standingService.resolveSeason(slugOrId));
    }

    public Page<ClanStandingResponse> standings(String slugOrId, Pageable pageable) {
        return standingService.ranking(standingService.resolveSeason(slugOrId), pageable);
    }

    @Transactional
    public void ensureCurrent() {
        Instant now = Instant.now();
        if (seasonRepository.existsOpenUntilAfter(now)) {
            return;
        }
        Instant start = seasonRepository.findTopByOrderByEndsAtDesc()
                .map(ClanSeason::getEndsAt)
                .filter(end -> end.isAfter(now))
                .orElse(now);
        long number = seasonRepository.count() + 1;
        seasonRepository.save(ClanSeason.builder()
                .name("Season " + number)
                .slug("season-" + number)
                .startsAt(start)
                .endsAt(start.atZone(ZoneOffset.UTC).plus(clanProperties.getSeasonLength()).toInstant())
                .build());
    }

    @Transactional
    public void close(UUID seasonId) {
        ClanSeason season = seasonRepository.findByIdForUpdate(seasonId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanSeason", seasonId));
        if (season.getClosedAt() != null) {
            return;
        }
        List<ClanSeasonStandingRepository.RankingRow> ranking = standingService.fullLiveRanking(seasonId);
        for (int i = 0; i < ranking.size(); i++) {
            ClanSeasonStandingRepository.RankingRow row = ranking.get(i);
            resultRepository.save(ClanSeasonResult.builder()
                    .season(season)
                    .clan(clanRepository.getReferenceById(row.getClanId()))
                    .rank(i + 1)
                    .baseStanding(row.getBaseStanding())
                    .earned(row.getEarned())
                    .build());
        }
        payRewards(season, ranking);
        season.setClosedAt(Instant.now());
        seasonRepository.save(season);
    }

    private void payRewards(ClanSeason season, List<ClanSeasonStandingRepository.RankingRow> ranking) {
        List<ClanSeasonReward> rewards = rewardRepository.findBySeasonId(season.getId());
        int lastRewardedRank = rewards.stream().mapToInt(ClanSeasonReward::getRankTo).max().orElse(0);
        Map<UUID, List<ClanWarParticipantRepository.ContributorView>> contributorsByClan = new HashMap<>();
        for (int i = 0; i < Math.min(ranking.size(), lastRewardedRank); i++) {
            int rank = i + 1;
            UUID clanId = ranking.get(i).getClanId();
            for (ClanSeasonReward reward : rewards) {
                if (rank < reward.getRankFrom() || rank > reward.getRankTo()) {
                    continue;
                }
                if (ClanCosmeticService.isClanCosmetic(reward.getItem().getType())) {
                    clanItemRepository.grantItem(clanId, reward.getItem().getId(), ClanItemSource.season.name(),
                            season.getId().toString());
                    continue;
                }
                List<ClanWarParticipantRepository.ContributorView> contributors = contributorsByClan.computeIfAbsent(
                        clanId, id -> participantRepository.findSeasonContributors(season.getId(), id));
                for (ClanWarParticipantRepository.ContributorView contributor : contributors) {
                    itemService.awardSystem(contributor.getUserId(), reward.getItem().getId(), ItemSource.clan_season,
                            season.getId().toString(), "Clan season reward: " + season.getName(),
                            reward.getQuantity());
                }
            }
        }
    }
}
