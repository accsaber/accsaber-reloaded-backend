package com.accsaber.backend.service.clan;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.clan.ClanAllianceResponse;
import com.accsaber.backend.model.dto.response.clan.ClanAuditEntryResponse;
import com.accsaber.backend.model.dto.response.clan.ClanJoinRequestResponse;
import com.accsaber.backend.model.dto.response.clan.ClanRivalResponse;
import com.accsaber.backend.model.dto.response.clan.ClanSeasonRewardResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarAidResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarHitResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarOrdersResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikePreviewResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarRefResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarRewardItemResponse;
import com.accsaber.backend.model.dto.response.clan.ClanXpGrantResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.entity.clan.ClanAlliance;
import com.accsaber.backend.model.entity.clan.ClanAuditEntry;
import com.accsaber.backend.model.entity.clan.ClanJoinRequest;
import com.accsaber.backend.model.entity.clan.ClanRival;
import com.accsaber.backend.model.entity.clan.ClanSeasonReward;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.entity.clan.war.ClanWarAid;
import com.accsaber.backend.model.entity.clan.war.ClanWarHit;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;
import com.accsaber.backend.repository.clan.ClanXpGrantRepository.HistoryRowView;
import com.accsaber.backend.service.item.ItemMapper;

public final class ClanMapper {

    private ClanMapper() {
    }

    public static ClanAllianceResponse toAllianceResponse(ClanAlliance alliance, UUID clanId, List<ItemResponse> allyEquipped,
            long warsFoughtTogether) {
        return new ClanAllianceResponse(alliance.getId(),
                PublicClanResponse.of(alliance.otherThan(clanId), allyEquipped), alliance.getStatus(),
                !alliance.getProposedByClan().getId().equals(clanId), ClanRefCache.playerRef(alliance.getProposedByUser()),
                ClanRefCache.playerRef(alliance.getEndedByUser()), warsFoughtTogether, alliance.getCreatedAt(), alliance.getAcceptedAt(),
                alliance.getEndedAt());
    }

    public static ClanAuditEntryResponse toAuditEntryResponse(ClanAuditEntry entry) {
        return new ClanAuditEntryResponse(entry.getId(), entry.getAction(), ClanRefCache.playerRef(entry.getActor()),
                ClanRefCache.playerRef(entry.getTargetUser()), entry.getDetails(), entry.getCreatedAt());
    }

    public static ClanJoinRequestResponse toJoinRequestResponse(ClanJoinRequest request, List<ItemResponse> equipped) {
        return new ClanJoinRequestResponse(request.getId(), PublicClanResponse.of(request.getClan(), equipped),
                ClanRefCache.playerRef(request.getUser()), request.getDirection(), request.getStatus(),
                ClanRefCache.playerRef(request.getCreatedBy()), ClanRefCache.playerRef(request.getResolvedBy()),
                request.getCreatedAt(), request.getResolvedAt());
    }

    public static ClanRivalResponse toRivalResponse(ClanRival rival, PublicClanResponse clan, boolean incoming) {
        return new ClanRivalResponse(clan, incoming, ClanRefCache.playerRef(rival.getDeclaredBy()), rival.getUpdatedAt());
    }

    public static ClanSeasonRewardResponse toSeasonRewardResponse(ClanSeasonReward reward) {
        return new ClanSeasonRewardResponse(reward.getId(), reward.getRankFrom(), reward.getRankTo(),
                ItemMapper.toItemResponse(reward.getItem()), reward.getQuantity());
    }

    public static ClanWarAidResponse toWarAidResponse(ClanWarAid aid, Map<UUID, PublicClanResponse> clans) {
        return new ClanWarAidResponse(aid.getId(), ClanWarRefResponse.of(aid.getWar()),
                clans.get(aid.getClan().getId()), clans.get(aid.getWar().lead(aid.getTeam()).getId()), aid.getTeam(),
                aid.getDirection(), aid.getStatus(), ClanRefCache.playerRef(aid.getRequestedBy()), aid.getCreatedAt(),
                aid.getResolvedAt());
    }

    public static ClanWarHitResponse toWarHitResponse(ClanWarHit hit, PublicMapDifficultyResponse difficulty) {
        return new ClanWarHitResponse(hit.getId(), ClanRefCache.playerRef(hit.getAttacker()), ClanRefCache.playerRef(hit.getVictim()),
                hit.getVictimCycle(), difficulty, hit.getVictimScore() == null, hit.isChip(), hit.getDamage(), hit.getGuardAfter(),
                hit.isBroke(), hit.getStandingMoved(), hit.getXpAwarded(), hit.getCreatedAt());
    }

    public record WarParticipantStats(double skill, ClanWarStrikePreviewResponse strikePreview, boolean showOrders) {
    }

    public static ClanWarParticipantResponse toWarParticipantResponse(ClanWarParticipant participant, PublicClanResponse clan,
            WarParticipantStats stats) {
        ClanWarOrdersResponse orders = stats.showOrders() ? new ClanWarOrdersResponse(participant.getTargeting(),
                participant.getOrder(), ClanRefCache.playerRef(participant.getOrderedTarget())) : null;
        return new ClanWarParticipantResponse(ClanRefCache.playerRef(participant.getUser()), clan,
                ClanRefCache.playerRef(participant.getTarget()), orders, stats.skill(), stats.strikePreview(),
                participant.getStandingWeight(), participant.getGuard(),
                participant.getGuardCycle(), participant.getBreaksSuffered(), participant.getBrokenAt(),
                participant.getContribution(),
                participant.getJoinedAt(), participant.getLeftAt());
    }

    public static ClanWarRewardItemResponse toWarRewardItemResponse(ClanWarRewardItem reward) {
        return new ClanWarRewardItemResponse(reward.getId(), ItemMapper.toItemResponse(reward.getItem()),
                reward.getQuantity(), reward.getTopContributors(), reward.isActive());
    }

    public static ClanXpGrantResponse toXpGrantResponse(HistoryRowView row) {
        return new ClanXpGrantResponse(row.getId(), ClanXpSource.valueOf(row.getSource()), row.getSourceId(),
                row.getRawAmount(), row.getRosterFactor(), row.getAmount(), row.getCreatedAt(), row.getGrants());
    }
}
