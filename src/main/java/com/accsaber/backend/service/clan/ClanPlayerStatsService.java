package com.accsaber.backend.service.clan;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.clan.ClanStatsResponse;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanPlayerStatsService {

    private final ClanMemberRepository memberRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanCosmeticService cosmeticService;

    public ClanStatsResponse forUser(Long userId) {
        ClanMember membership = memberRepository.findOpenByUserId(userId).orElse(null);
        ClanWarParticipantRepository.PlayerWarStatsView war = participantRepository.findPlayerWarStats(userId);
        return new ClanStatsResponse(
                membership == null ? null
                        : cosmeticService.publicRefs(List.of(membership.getClan())).get(membership.getClan().getId()),
                membership == null ? null : membership.getRole(),
                membership == null ? null : membership.getJoinedAt(),
                war.getWarsFought(), war.getWarsWon(), war.getHits(), war.getBreaksDealt(), war.getBreaksSuffered(),
                war.getStandingMoved(), war.getContribution(), war.getWarXp());
    }
}
