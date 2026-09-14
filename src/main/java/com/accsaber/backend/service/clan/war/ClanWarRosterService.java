package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoan;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.clan.war.ClanWarLoanRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.clan.ChatNotice;
import com.accsaber.backend.service.clan.ClanChatChannel;
import com.accsaber.backend.service.clan.ClanNotifier;
import com.accsaber.backend.service.clan.ClanStrengthService;
import com.accsaber.backend.service.clan.ClanStrengthService.MemberStrength;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClanWarRosterService {

    private final ClanWarRepository warRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanWarLoanRepository loanRepository;
    private final UserRepository userRepository;
    private final ClanStrengthService strengthService;
    private final ClanChatChannel chatChannel;
    private final ClanWarScoreGate scoreGate;
    private final ClanWarFeed feed;
    private final ClanNotifier notifier;
    private final ClanProperties clanProperties;

    @Transactional
    public void start(UUID warId) {
        ClanWar war = warRepository.findByIdForUpdate(warId).orElse(null);
        if (war == null || war.getStatus() != ClanWarStatus.preparing || war.getStartsAt().isAfter(Instant.now())) {
            return;
        }
        war.setStatus(ClanWarStatus.active);
        warRepository.saveAndFlush(war);
        sync(war);
        feed.war(war);
        notifier.warStarted(war);
        for (Clan clan : List.of(war.getAttackerClan(), war.getDefenderClan())) {
            chatChannel.announce(clan, ChatNotice.ofWar(ChatEvent.war_started, null, other(war, clan), war));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMembershipChanged(ClanMembershipChangedEvent event) {
        warRepository.findActiveIdsByClanId(event.clanId()).forEach(this::resync);
    }

    @Transactional
    public void resync(UUID warId) {
        warRepository.findByIdForUpdate(warId)
                .filter(war -> war.getStatus() == ClanWarStatus.active)
                .ifPresent(this::sync);
    }

    private void sync(ClanWar war) {
        List<ClanWarLoan> loans = loanRepository.findAcceptedByWarId(war.getId());
        Map<UUID, List<MemberStrength>> rosters = Map.of(
                war.getAttackerClan().getId(), roster(war.getAttackerClan(), loans),
                war.getDefenderClan().getId(), roster(war.getDefenderClan(), loans));
        Map<Long, Clan> lentBy = loans.stream()
                .collect(Collectors.toMap(loan -> loan.getUser().getId(), ClanWarLoan::getLendingClan));
        Map<Long, ClanWarParticipant> existing = participantRepository.findByWarId(war.getId()).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
        Instant now = Instant.now();
        for (ClanWarParticipant participant : existing.values()) {
            boolean stillIn = rosters.get(participant.getClan().getId()).stream()
                    .anyMatch(member -> member.userId().equals(participant.getUser().getId()));
            participant.setLeftAt(stillIn ? null : now);
        }
        for (Clan clan : List.of(war.getAttackerClan(), war.getDefenderClan())) {
            for (MemberStrength member : rosters.get(clan.getId())) {
                if (!existing.containsKey(member.userId())) {
                    existing.put(member.userId(), ClanWarParticipant.builder()
                            .war(war)
                            .user(userRepository.getReferenceById(member.userId()))
                            .clan(clan)
                            .lentByClan(lentBy.get(member.userId()))
                            .standingWeight(member.share())
                            .guard(clanProperties.getWar().getGuard())
                            .build());
                }
            }
        }
        assignDuelTargets(existing, war, rosters);
        participantRepository.saveAll(existing.values());
        scoreGate.refreshAfterCommit();
    }

    private List<MemberStrength> roster(Clan clan, List<ClanWarLoan> loans) {
        List<MemberStrength> roster = new ArrayList<>(strengthService.memberStrengths(clan.getId()));
        Map<UUID, List<ClanWarLoan>> lentByClan = loans.stream()
                .filter(loan -> loan.getClan().getId().equals(clan.getId()))
                .collect(Collectors.groupingBy(loan -> loan.getLendingClan().getId()));
        lentByClan.forEach((lendingClanId, lent) -> {
            List<Long> lentIds = lent.stream().map(loan -> loan.getUser().getId()).toList();
            strengthService.memberStrengths(lendingClanId).stream()
                    .filter(member -> lentIds.contains(member.userId()))
                    .forEach(roster::add);
        });
        roster.sort(Comparator.comparingDouble(MemberStrength::skill).reversed().thenComparing(MemberStrength::userId));
        return roster;
    }

    private void assignDuelTargets(Map<Long, ClanWarParticipant> participants, ClanWar war,
            Map<UUID, List<MemberStrength>> rosters) {
        participants.values().forEach(p -> p.setDuelTarget(null));
        List<ClanWarParticipant> attackers = fighting(participants, war.getAttackerClan(), rosters);
        List<ClanWarParticipant> defenders = fighting(participants, war.getDefenderClan(), rosters);
        pair(attackers, defenders);
        pair(defenders, attackers);
    }

    private List<ClanWarParticipant> fighting(Map<Long, ClanWarParticipant> participants, Clan clan,
            Map<UUID, List<MemberStrength>> rosters) {
        return rosters.get(clan.getId()).stream()
                .map(member -> participants.get(member.userId()))
                .filter(p -> p.getLeftAt() == null && p.getClan().getId().equals(clan.getId()))
                .toList();
    }

    private void pair(List<ClanWarParticipant> side, List<ClanWarParticipant> enemies) {
        if (enemies.isEmpty()) {
            return;
        }
        for (int i = 0; i < side.size(); i++) {
            int target = side.size() == 1 ? 0 : (int) Math.round(i * (enemies.size() - 1.0) / (side.size() - 1.0));
            side.get(i).setDuelTarget(enemies.get(target).getUser());
        }
    }

    private static Clan other(ClanWar war, Clan clan) {
        return war.getAttackerClan().getId().equals(clan.getId()) ? war.getDefenderClan() : war.getAttackerClan();
    }
}
