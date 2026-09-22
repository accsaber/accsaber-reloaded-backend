package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.model.dto.request.clan.CreateClanRequest;
import com.accsaber.backend.model.dto.request.clan.ModerateClanRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanRequest;
import com.accsaber.backend.model.dto.response.clan.ClanResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanAuditAction;
import com.accsaber.backend.model.entity.clan.ClanAuditEntry;
import com.accsaber.backend.model.entity.clan.ClanLeaveReason;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanAuditEntryRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.service.clan.war.ClanWarService;

@ExtendWith(MockitoExtension.class)
class ClanServiceTest {

    private static final Long PLAYER = 5L;

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
    private ClanStandingService standingService;
    @Mock
    private ClanAllianceService allianceService;
    @Mock
    private ClanMissionService missionService;
    @Mock
    private ClanWarService warService;
    @Mock
    private ClanNotifier notifier;
    @Mock
    private ClanRefCache refCache;
    @Spy
    private ClanProperties clanProperties = new ClanProperties();

    @InjectMocks
    private ClanService clanService;

    private final User player = User.builder().id(PLAYER).name("Founder").build();

    @BeforeEach
    void setUp() {
        lenient().when(accessService.player(PLAYER)).thenReturn(player);
        lenient().when(levelService.levelOf(any())).thenReturn(LevelResponse.builder().level(0).build());
        lenient().when(levelService.capacities()).thenReturn(new ClanLevelService.CapacityTable(List.of(), 50));
        lenient().when(cosmeticService.publicRefs(any())).thenAnswer(inv -> {
            Collection<Clan> clans = inv.getArgument(0);
            return clans.stream().collect(Collectors.toMap(Clan::getId, clan -> PublicClanResponse.of(clan, List.of())));
        });
        lenient().when(clanRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Clan clan = inv.getArgument(0);
            if (clan.getId() == null) {
                clan.setId(UUID.randomUUID());
            }
            return clan;
        });
    }

    @Nested
    class Create {

        private CreateClanRequest request(String name, String tag) {
            CreateClanRequest request = new CreateClanRequest();
            request.setName(name);
            request.setTag(tag);
            return request;
        }

        @Test
        void foundsTheClanWithAnUppercaseTagAndSeatsTheFounder() {
            ClanResponse response = clanService.create(PLAYER, request("  Night Owls ", "nOw"));

            ArgumentCaptor<Clan> saved = ArgumentCaptor.forClass(Clan.class);
            verify(clanRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("Night Owls");
            assertThat(saved.getValue().getTag()).isEqualTo("NOW");
            assertThat(saved.getValue().getSlug()).isEqualTo("night-owls");
            verify(roster).assertCanJoin(PLAYER);
            verify(roster).seat(saved.getValue(), player, ClanRole.founder);
            assertThat(response.clan().tag()).isEqualTo("NOW");
            verify(levelService).grantStartingItems(saved.getValue().getId());
        }

        @Test
        void theTagColourIsStoredLowercase() {
            CreateClanRequest request = request("Night Owls", "NOW");
            request.setTagColor("#FFAA00");

            assertThat(clanService.create(PLAYER, request).clan().tagColor()).isEqualTo("#ffaa00");
        }

        @Test
        void aTakenSlugGetsTheTagAppended() {
            when(clanRepository.existsBySlugAndActiveTrue("night-owls")).thenReturn(true);

            assertThat(clanService.create(PLAYER, request("Night Owls", "NOW")).clan().slug()).isEqualTo("night-owls-now");
        }

        @Test
        void aReservedSlugGetsTheTagAppended() {
            assertThat(clanService.create(PLAYER, request("Wars", "WAR")).clan().slug()).isEqualTo("wars-war");
        }

        @Test
        void aNameOrTagAlreadyInUseIsAConflict() {
            doThrow(new DataIntegrityViolationException("dup")).when(clanRepository).saveAndFlush(any());

            assertThatThrownBy(() -> clanService.create(PLAYER, request("Night Owls", "NOW")))
                    .isInstanceOf(ConflictException.class);
            verify(roster, never()).seat(any(), any(), any());
        }
    }

    @Nested
    class Update {

        private final UUID clanId = UUID.randomUUID();
        private final Clan clan = Clan.builder().id(clanId).name("Night Owls").tag("NOW").slug("night-owls").build();

        @BeforeEach
        void lock() {
            when(roster.lock(clanId)).thenReturn(clan);
        }

        @Test
        void onlyTheFounderCanCustomise() {
            when(accessService.require(clanId, PLAYER, ClanPermission.CUSTOMIZE)).thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> clanService.update(clanId, PLAYER, new UpdateClanRequest()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void aRenameMovesTheSlugAndIsAudited() {
            UpdateClanRequest request = new UpdateClanRequest();
            request.setName("Early Birds");

            clanService.update(clanId, PLAYER, request);

            assertThat(clan.getSlug()).isEqualTo("early-birds");
            ArgumentCaptor<ClanAuditEntry> audit = ArgumentCaptor.forClass(ClanAuditEntry.class);
            verify(auditRepository).save(audit.capture());
            assertThat(audit.getValue().getAction()).isEqualTo(ClanAuditAction.profile_updated);
            assertThat(audit.getValue().getDetails()).containsEntry("name", "Early Birds");
            verify(refCache).refreshAfterCommit(clanId);
        }

        @Test
        void aRecapitalisedNameKeepsItsSlug() {
            UpdateClanRequest request = new UpdateClanRequest();
            request.setName("NIGHT OWLS");

            clanService.update(clanId, PLAYER, request);

            assertThat(clan.getSlug()).isEqualTo("night-owls");
            verify(clanRepository, never()).existsBySlugAndActiveTrue(anyString());
        }

        @Test
        void aNewIconIsSavedAuditedAndReachesTheTagCache() {
            clanService.setIcon(clanId, PLAYER, "https://cdn.test/clan-icons/x.png?v=1");

            verify(accessService).require(clanId, PLAYER, ClanPermission.CUSTOMIZE);
            assertThat(clan.getIconUrl()).isEqualTo("https://cdn.test/clan-icons/x.png?v=1");
            ArgumentCaptor<ClanAuditEntry> audit = ArgumentCaptor.forClass(ClanAuditEntry.class);
            verify(auditRepository).save(audit.capture());
            assertThat(audit.getValue().getDetails()).containsEntry("icon", "updated");
            verify(refCache).refreshAfterCommit(clanId);
        }

        @Test
        void aTagColourChangeIsAudited() {
            UpdateClanRequest request = new UpdateClanRequest();
            request.setTagColor("#22C55E");

            clanService.update(clanId, PLAYER, request);

            assertThat(clan.getTagColor()).isEqualTo("#22c55e");
            ArgumentCaptor<ClanAuditEntry> audit = ArgumentCaptor.forClass(ClanAuditEntry.class);
            verify(auditRepository).save(audit.capture());
            assertThat(audit.getValue().getDetails()).containsEntry("tagColor", "#22c55e");
        }

        @Test
        void aBlankTagColourClearsIt() {
            clan.setTagColor("#22c55e");
            UpdateClanRequest request = new UpdateClanRequest();
            request.setTagColor("");

            clanService.update(clanId, PLAYER, request);

            assertThat(clan.getTagColor()).isNull();
            ArgumentCaptor<ClanAuditEntry> audit = ArgumentCaptor.forClass(ClanAuditEntry.class);
            verify(auditRepository).save(audit.capture());
            assertThat(audit.getValue().getDetails()).containsEntry("tagColor", "cleared");
        }

        @Test
        void nothingChangedWritesNothing() {
            UpdateClanRequest request = new UpdateClanRequest();
            request.setTag("now");

            clanService.update(clanId, PLAYER, request);

            verify(clanRepository, never()).saveAndFlush(any());
            verify(auditRepository, never()).save(any());
        }
    }

    @Test
    void disbandingDeactivatesClosesTheRosterEndsAlliancesAndMissionsAndAudits() {
        UUID clanId = UUID.randomUUID();
        Clan clan = Clan.builder().id(clanId).build();
        when(roster.lock(clanId)).thenReturn(clan);

        clanService.disband(clanId, PLAYER);

        verify(accessService).require(clanId, PLAYER, ClanPermission.DISBAND);
        assertThat(clan.isActive()).isFalse();
        verify(roster).closeAll(clanId, ClanLeaveReason.disbanded);
        verify(allianceService).endAll(eq(clan), any());
        verify(missionService).endAll(clanId);
        verify(warService).forfeitAll(clanId);
        verify(auditRepository).save(any(ClanAuditEntry.class));
    }

    @Nested
    class Staff {

        private final UUID clanId = UUID.randomUUID();
        private final Clan clan = Clan.builder().id(clanId).name("Night Owls").tag("NOW").slug("night-owls").build();

        @BeforeEach
        void lock() {
            when(roster.lock(clanId)).thenReturn(clan);
        }

        @Test
        void aModerationRenameSkipsTheFounderCheckAndAuditsTheReason() {
            UpdateClanRequest changes = new UpdateClanRequest();
            changes.setName("Early Birds");
            ModerateClanRequest request = new ModerateClanRequest();
            request.setChanges(changes);
            request.setReason("offensive name");

            clanService.moderate(clanId, request);

            verify(accessService, never()).require(any(), any(), any());
            ArgumentCaptor<ClanAuditEntry> audit = ArgumentCaptor.forClass(ClanAuditEntry.class);
            verify(auditRepository).save(audit.capture());
            assertThat(audit.getValue().getActor()).isNull();
            assertThat(audit.getValue().getDetails()).containsEntry("name", "Early Birds")
                    .containsEntry("reason", "offensive name");
        }

        @Test
        void staffCanTakeAnIconDownWithNoOtherChange() {
            clan.setIconUrl("https://cdn.test/clan-icons/x.png");
            ModerateClanRequest request = new ModerateClanRequest();
            request.setChanges(new UpdateClanRequest());
            request.setRemoveIcon(true);
            request.setReason("not safe for work");

            clanService.moderate(clanId, request);

            assertThat(clan.getIconUrl()).isNull();
            ArgumentCaptor<ClanAuditEntry> audit = ArgumentCaptor.forClass(ClanAuditEntry.class);
            verify(auditRepository).save(audit.capture());
            assertThat(audit.getValue().getDetails()).containsEntry("icon", "removed")
                    .containsEntry("reason", "not safe for work");
        }

        @Test
        void aStaffDisbandTellsEveryoneWhoWasInTheClanWhy() {
            when(memberRepository.findOpenUserIds(clanId)).thenReturn(List.of(1L, 2L));

            clanService.disbandByStaff(clanId, "cheating");

            InOrder order = inOrder(memberRepository, roster, notifier);
            order.verify(memberRepository).findOpenUserIds(clanId);
            order.verify(roster).closeAll(clanId, ClanLeaveReason.disbanded);
            order.verify(notifier).disbandedByStaff(clan, List.of(1L, 2L), "cheating");
            ArgumentCaptor<ClanAuditEntry> audit = ArgumentCaptor.forClass(ClanAuditEntry.class);
            verify(auditRepository).save(audit.capture());
            assertThat(audit.getValue().getAction()).isEqualTo(ClanAuditAction.disbanded);
            assertThat(audit.getValue().getDetails()).containsEntry("reason", "cheating");
        }
    }

    @Test
    void aClanIsFoundBySlugOrById() {
        Clan clan = Clan.builder().id(UUID.randomUUID()).slug("night-owls").build();
        when(clanRepository.findBySlugAndActiveTrue("night-owls")).thenReturn(Optional.of(clan));
        when(clanRepository.findByIdAndActiveTrue(clan.getId())).thenReturn(Optional.of(clan));

        assertThat(clanService.get("night-owls").clan().id()).isEqualTo(clan.getId());
        assertThat(clanService.get(clan.getId().toString()).clan().id()).isEqualTo(clan.getId());
        verify(memberRepository, times(2)).countOpenByClanIds(eq(List.of(clan.getId())));
    }
}
