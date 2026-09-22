package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanTrustResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarLoanResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoan;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarLoanRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.service.clan.ClanAccessService;
import com.accsaber.backend.service.clan.ClanAllianceService;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanNotifier;
import com.accsaber.backend.service.clan.ClanPermission;
import com.accsaber.backend.service.clan.ClanRoster;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanWarLoanService {

    private final ClanWarLoanRepository loanRepository;
    private final ClanWarRepository warRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanRoster roster;
    private final ClanAccessService accessService;
    private final ClanAllianceService allianceService;
    private final ClanLevelService levelService;
    private final ClanCosmeticService cosmeticService;
    private final ClanWarRosterService rosterService;
    private final ClanNotifier notifier;
    private final ClanProperties clanProperties;

    @Transactional
    public ClanWarLoanResponse offer(UUID warId, Long playerId, Long lentUserId, UUID clanId) {
        User actor = accessService.player(playerId);
        ClanMember membership = memberRepository.findOpenByUserId(actor.getId())
                .orElseThrow(() -> new ForbiddenException("You are not in a clan"));
        Clan lending = roster.lock(membership.getClan().getId());
        accessService.require(lending.getId(), actor.getId(), ClanPermission.LEND);
        ClanWar war = warRepository.findByIdForUpdate(warId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanWar", warId));
        Clan receiving = sideOf(war, clanId);
        if (war.getStatus() == ClanWarStatus.ended) {
            throw new ConflictException("This war is already over");
        }
        if (Set.of(war.getAttackerClan().getId(), war.getDefenderClan().getId()).contains(lending.getId())) {
            throw new ValidationException("Your clan is fighting in this war itself");
        }
        User lent = accessService.player(lentUserId);
        if (memberRepository.findOpenByClanIdAndUserId(lending.getId(), lent.getId()).isEmpty()) {
            throw new ValidationException("userId", "must be a member of your clan");
        }
        assertCanLend(war, lending, receiving, lent);
        ClanWarLoan loan = loanRepository.saveAndFlush(ClanWarLoan.builder()
                .war(war)
                .clan(receiving)
                .lendingClan(lending)
                .user(lent)
                .offeredBy(actor)
                .build());
        notifier.loanOffered(loan);
        return responses(List.of(loan)).getFirst();
    }

    @Transactional
    public ClanWarLoanResponse resolve(UUID loanId, Long playerId, ClanWarLoanStatus status) {
        User actor = accessService.player(playerId);
        ClanWarLoan loan = loanRepository.findWithRefsById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanWarLoan", loanId));
        roster.lock(loan.getLendingClan().getId());
        ClanWar war = warRepository.findByIdForUpdate(loan.getWar().getId()).orElseThrow();
        if (loan.getStatus() != ClanWarLoanStatus.pending) {
            throw new ConflictException("This loan has already been answered");
        }
        switch (status) {
            case accepted, declined -> {
                if (!loan.getUser().getId().equals(actor.getId())) {
                    throw new ForbiddenException("Only the player being lent can answer a loan");
                }
            }
            case cancelled -> accessService.require(loan.getLendingClan().getId(), actor.getId(), ClanPermission.LEND);
            case pending, ended -> throw new ValidationException("status", "must be accepted, declined or cancelled");
        }
        if (status == ClanWarLoanStatus.accepted && war.getStatus() == ClanWarStatus.ended) {
            throw new ConflictException("This war is already over");
        }
        loan.setStatus(status);
        loan.setResolvedAt(Instant.now());
        loanRepository.saveAndFlush(loan);
        if (status == ClanWarLoanStatus.accepted) {
            rosterService.resync(war.getId());
        }
        return responses(List.of(loan)).getFirst();
    }

    public Page<ClanWarLoanResponse> list(UUID warId, Long userId, ClanWarLoanStatus status, Pageable pageable) {
        Page<ClanWarLoan> page = loanRepository.findPage(warId, userId, status, pageable);
        return new PageImpl<>(responses(page.getContent()), pageable, page.getTotalElements());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMembershipChanged(ClanMembershipChangedEvent event) {
        List<ClanWarLoan> open = loanRepository.findOpenByLendingClanId(event.clanId());
        if (open.isEmpty()) {
            return;
        }
        Set<Long> members = Set.copyOf(memberRepository.findOpenUserIds(event.clanId()));
        Instant now = Instant.now();
        for (ClanWarLoan loan : open) {
            if (members.contains(loan.getUser().getId())) {
                continue;
            }
            boolean accepted = loan.getStatus() == ClanWarLoanStatus.accepted;
            loan.setStatus(accepted ? ClanWarLoanStatus.ended : ClanWarLoanStatus.cancelled);
            loan.setResolvedAt(loan.getResolvedAt() != null ? loan.getResolvedAt() : now);
            loan.setEndedAt(accepted ? now : null);
            loanRepository.saveAndFlush(loan);
            if (accepted) {
                rosterService.resync(loan.getWar().getId());
            }
        }
    }

    private void assertCanLend(ClanWar war, Clan lending, Clan receiving, User lent) {
        ClanTrustResponse trust = allianceService.trustBetween(lending.getId(), receiving.getId())
                .orElseThrow(() -> new ConflictException("You can only lend players to an ally"));
        if (loanRepository.existsOpenByUserId(lent.getId())) {
            throw new ConflictException("That player is already out on a loan");
        }
        loanRepository.findLastEndedAt(lent.getId())
                .map(ended -> ended.plus(clanProperties.getWar().getLoanCooldown()))
                .filter(free -> free.isAfter(Instant.now()))
                .ifPresent(free -> {
                    throw new ValidationException("That player can be lent again from " + free);
                });
        if (loanRepository.countOpenByLendingClanId(lending.getId())
                >= levelService.capacityOf(lending, ClanCapacity.lend_slots)) {
            throw new ConflictException("Your clan has no free lend slots");
        }
        if (receiving.getId().equals(war.getAttackerClan().getId()) && loanRepository.countOpenIntoWar(war.getId(),
                receiving.getId()) >= levelService.capacityOf(receiving, ClanCapacity.receive_slots)) {
            throw new ConflictException("The attacking clan has no free slots for borrowed players");
        }
        if (loanRepository.countOpenBetween(lending.getId(), receiving.getId()) >= trust.loanCap()) {
            throw new ConflictException("This alliance does not trust you with another loan yet");
        }
    }

    private Clan sideOf(ClanWar war, UUID clanId) {
        return Stream.of(war.getAttackerClan(), war.getDefenderClan())
                .filter(clan -> clan.getId().equals(clanId))
                .findFirst()
                .orElseThrow(() -> new ValidationException("clanId", "must be one of the clans in this war"));
    }

    private List<ClanWarLoanResponse> responses(List<ClanWarLoan> loans) {
        Map<UUID, PublicClanResponse> clans = cosmeticService.publicRefs(loans.stream()
                .flatMap(loan -> Stream.of(loan.getClan(), loan.getLendingClan()))
                .collect(Collectors.toList()));
        return loans.stream().map(loan -> ClanWarLoanResponse.of(loan, clans)).toList();
    }
}
