package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanAlliance;
import com.accsaber.backend.model.entity.clan.ClanAllianceStatus;
import com.accsaber.backend.model.entity.clan.ClanAuditAction;
import com.accsaber.backend.model.entity.clan.ClanAuditEntry;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanAllianceRepository;
import com.accsaber.backend.repository.clan.ClanAuditEntryRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.clan.ClanRivalRepository;

@ExtendWith(MockitoExtension.class)
class ClanAllianceServiceTest {

    private static final UUID OWLS_ID = new UUID(1L, 1L);
    private static final UUID LAPIZ_ID = new UUID(-1L, 1L);

    @Mock
    private ClanNotifier notifier;
    @Mock
    private ClanAllianceRepository allianceRepository;
    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanAuditEntryRepository auditRepository;
    @Mock
    private ClanRoster roster;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanLevelService levelService;
    @Mock
    private ClanCosmeticService cosmeticService;
    @Mock
    private ClanStrengthService strengthService;
    @Mock
    private ClanChatChannel chatChannel;
    @Mock
    private ClanRivalRepository rivalRepository;
    @Spy
    private ClanProperties clanProperties = new ClanProperties();

    @InjectMocks
    private ClanAllianceService service;

    private final Clan owls = Clan.builder().id(OWLS_ID).name("Night Owls").tag("NOW").slug("night-owls").build();
    private final Clan lapiz = Clan.builder().id(LAPIZ_ID).name("El Lapiz").tag("LPZ").slug("el-lapiz").build();
    private final User owlsFounder = User.builder().id(1L).name("Owl").build();
    private final User lapizFounder = User.builder().id(2L).name("Lapiz").build();
    private final User outsider = User.builder().id(3L).name("Outsider").build();

    @BeforeEach
    void setUp() {
        lenient().when(accessService.player(1L)).thenReturn(owlsFounder);
        lenient().when(accessService.player(2L)).thenReturn(lapizFounder);
        lenient().when(accessService.player(3L)).thenReturn(outsider);
        lenient().when(memberRepository.findOpenByUserId(1L)).thenReturn(Optional.of(member(owls, owlsFounder)));
        lenient().when(memberRepository.findOpenByUserId(2L)).thenReturn(Optional.of(member(lapiz, lapizFounder)));
        lenient().when(roster.lockPair(any(), any())).thenReturn(List.of(owls, lapiz));
        lenient().when(levelService.capacityOf(any(), any())).thenReturn(2);
        lenient().when(cosmeticService.equippedByClanIds(anyCollection())).thenReturn(Map.of());
        lenient().when(allianceRepository.saveAndFlush(any())).thenAnswer(inv -> {
            ClanAlliance alliance = inv.getArgument(0);
            if (alliance.getId() == null) {
                alliance.setId(UUID.randomUUID());
            }
            return alliance;
        });
    }

    private ClanMember member(Clan clan, User user) {
        return ClanMember.builder().clan(clan).user(user).role(ClanRole.founder).build();
    }

    private ClanAlliance alliance(ClanAllianceStatus status, Clan proposer, Instant acceptedAt) {
        ClanAlliance alliance = ClanAlliance.builder().id(UUID.randomUUID()).clanA(owls).clanB(lapiz)
                .proposedByClan(proposer).proposedByUser(proposer == owls ? owlsFounder : lapizFounder)
                .status(status).acceptedAt(acceptedAt).build();
        lenient().when(allianceRepository.findWithRefsById(alliance.getId())).thenReturn(Optional.of(alliance));
        return alliance;
    }

    @Nested
    class Propose {

        @Test
        void aFounderProposesAndTheRowStoresTheOrderedPair() {
            var response = service.propose(LAPIZ_ID, 2L, OWLS_ID);

            verify(accessService).require(LAPIZ_ID, 2L, ClanPermission.MANAGE_ALLIANCES);
            ArgumentCaptor<ClanAlliance> saved = ArgumentCaptor.forClass(ClanAlliance.class);
            verify(allianceRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getClanA()).isSameAs(owls);
            assertThat(saved.getValue().getClanB()).isSameAs(lapiz);
            assertThat(saved.getValue().getProposedByClan()).isSameAs(lapiz);
            assertThat(response.ally().id()).isEqualTo(OWLS_ID);
            assertThat(response.incoming()).isFalse();
            assertThat(response.trust()).isNull();
            verify(notifier).allianceProposed(saved.getValue(), owls);
        }

        @Test
        void aClanCannotAllyWithItself() {
            assertThatThrownBy(() -> service.propose(OWLS_ID, 1L, OWLS_ID)).isInstanceOf(ValidationException.class);
        }

        @Test
        void anOpenAllianceOrProposalBlocksAnother() {
            when(allianceRepository.existsOpenBetween(OWLS_ID, LAPIZ_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.propose(OWLS_ID, 1L, LAPIZ_ID)).isInstanceOf(ConflictException.class);
        }

        @Test
        void rivalsCannotProposeAnAlliance() {
            when(rivalRepository.existsActiveBetween(LAPIZ_ID, OWLS_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.propose(LAPIZ_ID, 2L, OWLS_ID)).isInstanceOf(ConflictException.class);
            verify(allianceRepository, never()).saveAndFlush(any());
        }

        @Test
        void theProposerNeedsAFreeAllySlot() {
            when(allianceRepository.countActiveByClanId(OWLS_ID)).thenReturn(2L);

            assertThatThrownBy(() -> service.propose(OWLS_ID, 1L, LAPIZ_ID)).isInstanceOf(ConflictException.class);
            verify(levelService).capacityOf(owls, ClanCapacity.ally_slots);
            verify(allianceRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class Resolve {

        @Test
        void theReceivingClanAcceptsWhenBothHaveSlots() {
            ClanAlliance alliance = alliance(ClanAllianceStatus.pending, owls, null);

            var response = service.resolve(alliance.getId(), 2L, ClanAllianceStatus.active);

            assertThat(alliance.getStatus()).isEqualTo(ClanAllianceStatus.active);
            assertThat(alliance.getAcceptedAt()).isNotNull();
            verify(allianceRepository).countActiveByClanId(OWLS_ID);
            verify(allianceRepository).countActiveByClanId(LAPIZ_ID);
            ArgumentCaptor<ClanAuditEntry> audits = ArgumentCaptor.forClass(ClanAuditEntry.class);
            verify(auditRepository, times(2)).save(audits.capture());
            assertThat(audits.getAllValues()).extracting(ClanAuditEntry::getAction)
                    .containsOnly(ClanAuditAction.alliance_formed);
            verify(chatChannel).announce(owls, ChatNotice.ofClan(ChatEvent.alliance_formed, lapizFounder, lapiz));
            verify(chatChannel).announce(lapiz, ChatNotice.ofClan(ChatEvent.alliance_formed, lapizFounder, owls));
            verify(strengthService).recompute(List.of(OWLS_ID, LAPIZ_ID));
            verify(notifier).allianceChanged(alliance, owls, lapizFounder, "accepted");
            assertThat(response.ally().id()).isEqualTo(OWLS_ID);
            assertThat(response.incoming()).isTrue();
            assertThat(response.trust().level()).isZero();
            assertThat(response.trust().loanCap()).isEqualTo(1);
        }

        @Test
        void theProposingClanCannotAcceptItsOwnProposal() {
            ClanAlliance alliance = alliance(ClanAllianceStatus.pending, owls, null);

            assertThatThrownBy(() -> service.resolve(alliance.getId(), 1L, ClanAllianceStatus.active))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void acceptingFailsWhenTheOtherClanIsFull() {
            ClanAlliance alliance = alliance(ClanAllianceStatus.pending, owls, null);
            when(allianceRepository.countActiveByClanId(OWLS_ID)).thenReturn(2L);

            assertThatThrownBy(() -> service.resolve(alliance.getId(), 2L, ClanAllianceStatus.active))
                    .isInstanceOf(ConflictException.class);
            assertThat(alliance.getStatus()).isEqualTo(ClanAllianceStatus.pending);
        }

        @Test
        void theProposerWithdrawsByDeclining() {
            ClanAlliance alliance = alliance(ClanAllianceStatus.pending, owls, null);

            service.resolve(alliance.getId(), 1L, ClanAllianceStatus.declined);

            assertThat(alliance.getStatus()).isEqualTo(ClanAllianceStatus.declined);
            assertThat(alliance.getEndedByUser()).isSameAs(owlsFounder);
            verify(strengthService, never()).recompute(any());
        }

        @Test
        void eitherFounderEndsAnActiveAlliance() {
            ClanAlliance alliance = alliance(ClanAllianceStatus.active, owls, Instant.now());

            service.resolve(alliance.getId(), 2L, ClanAllianceStatus.ended);

            assertThat(alliance.getStatus()).isEqualTo(ClanAllianceStatus.ended);
            assertThat(alliance.getEndedAt()).isNotNull();
            verify(auditRepository, times(2)).save(any(ClanAuditEntry.class));
            verify(strengthService).recompute(List.of(OWLS_ID, LAPIZ_ID));
        }

        @Test
        void onlyAnActiveAllianceCanEnd() {
            ClanAlliance alliance = alliance(ClanAllianceStatus.pending, owls, null);

            assertThatThrownBy(() -> service.resolve(alliance.getId(), 2L, ClanAllianceStatus.ended))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void pendingIsNotAnAnswer() {
            ClanAlliance alliance = alliance(ClanAllianceStatus.pending, owls, null);

            assertThatThrownBy(() -> service.resolve(alliance.getId(), 2L, ClanAllianceStatus.pending))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void aPlayerOutsideBothClansIsTurnedAway() {
            ClanAlliance alliance = alliance(ClanAllianceStatus.active, owls, Instant.now());
            when(memberRepository.findOpenByUserId(3L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolve(alliance.getId(), 3L, ClanAllianceStatus.ended))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void trustClimbsWithAgeAndContributionTogether() {
        ClanAlliance seasoned = alliance(ClanAllianceStatus.active, owls, Instant.now().minus(40, ChronoUnit.DAYS));
        ClanAlliance fresh = alliance(ClanAllianceStatus.active, owls, Instant.now().minus(2, ChronoUnit.DAYS));
        when(clanRepository.findByIdAndActiveTrue(OWLS_ID)).thenReturn(Optional.of(owls));
        when(allianceRepository.findPageByClanIdAndStatus(OWLS_ID, ClanAllianceStatus.active, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(seasoned, fresh)));
        when(allianceRepository.findTrustContributions(List.of(seasoned.getId(), fresh.getId())))
                .thenReturn(List.of(trust(seasoned.getId(), 600.0), trust(fresh.getId(), 5000.0)));

        var page = service.list(OWLS_ID, Pageable.unpaged()).getContent();

        assertThat(page.get(0).trust().level()).isEqualTo(1);
        assertThat(page.get(0).trust().loanCap()).isEqualTo(2);
        assertThat(page.get(1).trust().level()).isZero();
        assertThat(page.get(1).trust().contribution()).isEqualTo(5000.0);
        assertThat(page.get(0).ally().id()).isEqualTo(LAPIZ_ID);
    }

    @Test
    void disbandingEndsActiveAlliancesAndDeclinesProposals() {
        Clan rivals = Clan.builder().id(UUID.randomUUID()).name("Rivals").tag("RIV").build();
        ClanAlliance active = alliance(ClanAllianceStatus.active, owls, Instant.now());
        ClanAlliance pending = ClanAlliance.builder().id(UUID.randomUUID()).clanA(owls).clanB(rivals)
                .proposedByClan(rivals).status(ClanAllianceStatus.pending).build();
        when(allianceRepository.findOpenByClanId(OWLS_ID)).thenReturn(List.of(active, pending));

        service.endAll(owls, owlsFounder);

        assertThat(active.getStatus()).isEqualTo(ClanAllianceStatus.ended);
        assertThat(pending.getStatus()).isEqualTo(ClanAllianceStatus.declined);
        ArgumentCaptor<ClanAuditEntry> audit = ArgumentCaptor.forClass(ClanAuditEntry.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getClan()).isSameAs(lapiz);
        verify(strengthService).recompute(List.of(LAPIZ_ID));
    }

    private ClanAllianceRepository.TrustView trust(UUID allianceId, double contribution) {
        return new ClanAllianceRepository.TrustView() {
            public UUID getAllianceId() {
                return allianceId;
            }

            public double getContribution() {
                return contribution;
            }
        };
    }

    @Test
    void trustBetweenTwoClansReadsTheirActiveAlliance() {
        ClanAlliance seasoned = alliance(ClanAllianceStatus.active, owls, Instant.now().minus(40, ChronoUnit.DAYS));
        when(allianceRepository.findActiveBetween(LAPIZ_ID, OWLS_ID)).thenReturn(Optional.of(seasoned));
        when(allianceRepository.findTrustContributions(List.of(seasoned.getId())))
                .thenReturn(List.of(trust(seasoned.getId(), 600.0)));

        assertThat(service.trustBetween(LAPIZ_ID, OWLS_ID)).get()
                .satisfies(trust -> assertThat(trust.loanCap()).isEqualTo(2));
        assertThat(service.trustBetween(OWLS_ID, UUID.randomUUID())).isEmpty();
    }
}
