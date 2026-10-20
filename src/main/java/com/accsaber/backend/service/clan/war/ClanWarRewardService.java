package com.accsaber.backend.service.clan.war;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.clan.ClanWarRewardItemRequest;
import com.accsaber.backend.model.dto.response.clan.ClanWarRewardItemResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarRewardsResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.repository.clan.ClanXpGrantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRewardItemRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanMapper;
import com.accsaber.backend.service.clan.ClanRefCache;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanWarRewardService {

    private final ClanWarRewardItemRepository rewardItemRepository;
    private final ItemRepository itemRepository;
    private final ClanWarRepository warRepository;
    private final ClanWarSideRepository sideRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanWarHitRepository hitRepository;
    private final ClanXpGrantRepository xpGrantRepository;
    private final ClanCosmeticService cosmeticService;

    public List<ClanWarRewardItemResponse> list() {
        return rewardItemRepository.findAllWithItems().stream().map(ClanMapper::toWarRewardItemResponse).toList();
    }

    public ClanWarRewardsResponse rewards(UUID warId) {
        ClanWar war = warRepository.findById(warId).orElseThrow(() -> new ResourceNotFoundException("ClanWar", warId));
        List<ClanWarSide> sides = sideRepository.findByWarId(warId);
        Map<UUID, PublicClanResponse> clans = cosmeticService.publicRefs(sides.stream().map(ClanWarSide::getClan).toList());
        List<ClanWarRewardItem> pool = rewardItemRepository.findActiveWithItems();
        List<ClanWarSide> winners = war.getStatus() == ClanWarStatus.ended
                ? ClanWarSettlementService.winners(war, sides) : List.of();
        Set<UUID> winnerClans = winners.stream().map(side -> side.getClan().getId()).collect(Collectors.toSet());
        Map<UUID, Double> clanXp = xpGrantRepository.warClanXp(warId);
        List<ClanWarRewardItemResponse> cosmetics = pool.stream()
                .filter(reward -> reward.getItem().getType().isClanCosmetic())
                .map(ClanMapper::toWarRewardItemResponse).toList();
        List<ClanWarRewardsResponse.ClanReward> clanRewards = sides.stream()
                .map(side -> new ClanWarRewardsResponse.ClanReward(clans.get(side.getClan().getId()),
                        clanXp.getOrDefault(side.getClan().getId(), 0.0),
                        winnerClans.contains(side.getClan().getId()) ? cosmetics : List.of()))
                .toList();
        List<ClanWarParticipant> participants = participantRepository.findByWarIdInContributionOrder(warId);
        Map<Long, Integer> ranks = ClanWarSettlementService.rewardRanks(participants, winners);
        Map<Long, Double> breakXp = hitRepository.breakXp(warId);
        List<ClanWarRewardsResponse.PlayerReward> players = participants.stream()
                .map(p -> playerReward(p, clans.get(p.getClan().getId()), pool,
                        ranks.getOrDefault(p.getUser().getId(), 0), breakXp.getOrDefault(p.getUser().getId(), 0.0)))
                .toList();
        return new ClanWarRewardsResponse(pool.stream().map(ClanMapper::toWarRewardItemResponse).toList(),
                clanRewards, players);
    }

    @Transactional
    public ClanWarRewardItemResponse create(ClanWarRewardItemRequest request) {
        if (request.getItemId() == null) {
            throw new ValidationException("itemId", "is required");
        }
        ClanWarRewardItem reward = ClanWarRewardItem.builder()
                .item(itemRepository.findById(request.getItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Item", request.getItemId())))
                .build();
        return ClanMapper.toWarRewardItemResponse(rewardItemRepository.save(apply(reward, request)));
    }

    @Transactional
    public ClanWarRewardItemResponse update(UUID rewardId, ClanWarRewardItemRequest request) {
        return ClanMapper.toWarRewardItemResponse(rewardItemRepository.save(apply(find(rewardId), request)));
    }

    @Transactional
    public void deactivate(UUID rewardId) {
        find(rewardId).setActive(false);
    }

    private ClanWarRewardItem find(UUID rewardId) {
        return rewardItemRepository.findById(rewardId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanWarRewardItem", rewardId));
    }

    private static ClanWarRewardsResponse.PlayerReward playerReward(ClanWarParticipant participant,
            PublicClanResponse clan, List<ClanWarRewardItem> pool, int rank, double breakXp) {
        double xp = breakXp + (participant.getXpAwarded() == null ? 0.0 : participant.getXpAwarded());
        return new ClanWarRewardsResponse.PlayerReward(ClanRefCache.playerRef(participant.getUser()), clan, xp,
                pool.stream().filter(reward -> ClanWarSettlementService.paysPlayer(reward, rank))
                        .map(ClanMapper::toWarRewardItemResponse).toList());
    }

    private static ClanWarRewardItem apply(ClanWarRewardItem reward, ClanWarRewardItemRequest request) {
        if (request.getQuantity() != null) {
            reward.setQuantity(request.getQuantity());
        }
        if (request.getTopContributors() != null) {
            reward.setTopContributors(request.getTopContributors());
        }
        if (request.getActive() != null) {
            reward.setActive(request.getActive());
        }
        return reward;
    }
}
