package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanLevelStepResponse;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanLevelCapacity;
import com.accsaber.backend.model.entity.clan.ClanLevelItem;
import com.accsaber.backend.model.entity.clan.ClanLevelWarMode;
import com.accsaber.backend.model.entity.clan.ClanWarModeAxis;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.clan.ClanItemRepository;
import com.accsaber.backend.repository.clan.ClanLevelCapacityRepository;
import com.accsaber.backend.repository.clan.ClanLevelItemRepository;
import com.accsaber.backend.repository.clan.ClanLevelWarModeRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.clan.ClanXpGrantRepository;
import com.accsaber.backend.repository.item.ItemRepository;

@ExtendWith(MockitoExtension.class)
class ClanLevelServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private CurveRepository curveRepository;
    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanXpGrantRepository grantRepository;
    @Mock
    private ClanItemRepository clanItemRepository;
    @Mock
    private ClanLevelCapacityRepository capacityRepository;
    @Mock
    private ClanLevelWarModeRepository warModeRepository;
    @Mock
    private ClanLevelItemRepository levelItemRepository;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanLevelService levelService;

    @BeforeEach
    void setUp() {
        levelService = new ClanLevelService(curveRepository, clanRepository, memberRepository, grantRepository,
                clanItemRepository, capacityRepository, warModeRepository, levelItemRepository, itemRepository,
                clanProperties);
        lenient().when(curveRepository.findById(UUID.fromString("acc00000-0000-0000-0000-000000000030")))
                .thenReturn(Optional.of(Curve.builder().xParameterValue(100.0).yParameterValue(1.0).build()));
    }

    private ClanLevelCapacity row(int level, ClanCapacity capacity, int amount) {
        return ClanLevelCapacity.builder().level(level).capacity(capacity).amount(amount).build();
    }

    @Test
    void levelComesFromTheClanCurve() {
        assertThat(levelService.levelOf(Clan.builder().totalXp(350.0).build()).getLevel()).isEqualTo(2);
    }

    @Nested
    class Capacities {

        @Test
        void capacitiesStackEveryRowAtOrBelowTheLevel() {
            when(capacityRepository.findAll()).thenReturn(List.of(
                    row(0, ClanCapacity.mission_slots, 1),
                    row(3, ClanCapacity.mission_slots, 1),
                    row(9, ClanCapacity.mission_slots, 1),
                    row(3, ClanCapacity.officer_slots, 2)));

            ClanLevelService.CapacityTable table = levelService.capacities();

            assertThat(table.at(0, ClanCapacity.mission_slots)).isEqualTo(1);
            assertThat(table.at(5, ClanCapacity.mission_slots)).isEqualTo(2);
            assertThat(table.at(5, ClanCapacity.officer_slots)).isEqualTo(2);
            assertThat(table.at(2, ClanCapacity.officer_slots)).isZero();
        }

        @Test
        void memberSlotsNeverPassTheHardCap() {
            clanProperties.setMaxMembers(12);
            when(capacityRepository.findAll()).thenReturn(List.of(
                    row(0, ClanCapacity.member_slots, 10),
                    row(4, ClanCapacity.member_slots, 10)));

            assertThat(levelService.capacities().at(4, ClanCapacity.member_slots)).isEqualTo(12);
        }
    }

    @Nested
    class GrantXp {

        private final UUID clanId = UUID.randomUUID();

        private Clan lockedClan(double totalXp, double rosterStrength) {
            Clan clan = Clan.builder().id(clanId).totalXp(totalXp).rosterStrength(rosterStrength).build();
            when(clanRepository.findByIdAndActiveTrueForUpdate(clanId)).thenReturn(Optional.of(clan));
            return clan;
        }

        @Test
        void aRosterScaledGrantIsDividedByTheRosterFactor() {
            clanProperties.setRosterReferenceStrength(280.0);
            clanProperties.setRosterReferenceMembers(5.0);
            clanProperties.setRosterFactorExponent(0.5);
            Clan clan = lockedClan(0.0, 560.0);
            when(memberRepository.countByClan_IdAndLeftAtIsNull(clanId)).thenReturn(10L);
            when(grantRepository.insertIfAbsent(clanId, "daily_play", "2026-09-13", 90.0, 2.0, 45.0)).thenReturn(1);

            boolean granted = levelService.grantXp(clanId,
                    new ClanXpAward(90.0, ClanXpSource.daily_play, "2026-09-13", true));

            assertThat(granted).isTrue();
            assertThat(clan.getTotalXp()).isEqualTo(45.0);
        }

        @Test
        void aSmallRosterNeverGetsMultipliedUp() {
            clanProperties.setRosterReferenceStrength(280.0);
            clanProperties.setRosterReferenceMembers(5.0);
            assertThat(levelService.rosterFactor(Clan.builder().rosterStrength(50.0).build(), 3)).isEqualTo(1.0);
        }

        @Test
        void theFactorGrowsWithHeadcountEvenWhenStrengthStopsGrowing() {
            clanProperties.setRosterReferenceStrength(280.0);
            clanProperties.setRosterReferenceMembers(5.0);
            clanProperties.setRosterFactorExponent(0.5);
            Clan strong = Clan.builder().rosterStrength(560.0).build();
            assertThat(levelService.rosterFactor(strong, 20)).isEqualTo(Math.sqrt(8.0));
            assertThat(levelService.rosterFactor(strong, 50)).isEqualTo(Math.sqrt(20.0));
        }

        @Test
        void aRepeatedSourceBanksNothing() {
            Clan clan = lockedClan(10.0, 0.0);

            boolean granted = levelService.grantXp(clanId,
                    new ClanXpAward(90.0, ClanXpSource.mission, "m-1", false));

            assertThat(granted).isFalse();
            assertThat(clan.getTotalXp()).isEqualTo(10.0);
            verify(clanRepository, never()).saveAndFlush(clan);
        }

        @Test
        void crossingLevelsGrantsEveryItemInBetween() {
            lockedClan(50.0, 0.0);
            when(grantRepository.insertIfAbsent(eq(clanId), anyString(), anyString(), anyDouble(), anyDouble(),
                    anyDouble())).thenReturn(1);

            levelService.grantXp(clanId, new ClanXpAward(600.0, ClanXpSource.war_win, "w-1", false));

            verify(clanItemRepository).grantLevelItems(clanId, 0, 3);
        }

        @Test
        void stayingInTheSameLevelGrantsNoItems() {
            lockedClan(10.0, 0.0);
            when(grantRepository.insertIfAbsent(eq(clanId), anyString(), anyString(), anyDouble(), anyDouble(),
                    anyDouble())).thenReturn(1);

            levelService.grantXp(clanId, new ClanXpAward(20.0, ClanXpSource.mission, "m-2", false));

            verify(clanItemRepository, never()).grantLevelItems(eq(clanId), anyInt(), anyInt());
        }

        @Test
        void aDisbandedClanBanksNothing() {
            assertThat(levelService.grantXp(clanId, new ClanXpAward(20.0, ClanXpSource.mission, "m-3", false)))
                    .isFalse();
        }
    }

    @Test
    void theUnlockTableHasOneStepPerLevelThatUnlocksSomething() {
        when(capacityRepository.findAll()).thenReturn(List.of(
                row(0, ClanCapacity.member_slots, 10),
                row(2, ClanCapacity.member_slots, 5)));
        when(warModeRepository.findAll()).thenReturn(List.of(
                ClanLevelWarMode.builder().axis(ClanWarModeAxis.arena).mode("mixed").level(0).build(),
                ClanLevelWarMode.builder().axis(ClanWarModeAxis.ruleset).mode("berserker").level(2).build()));

        List<ClanLevelStepResponse> steps = levelService.table();

        assertThat(steps).extracting(ClanLevelStepResponse::level).containsExactly(0, 2);
        assertThat(steps.get(0).unlocks().arenas()).containsExactly(ClanArena.mixed);
        assertThat(steps.get(1).totalXpRequired()).isEqualTo(300.0);
        assertThat(steps.get(1).unlocks().capacities()).containsEntry(ClanCapacity.member_slots, 5);
        assertThat(steps.get(1).unlocks().rulesets()).containsExactly(ClanRuleset.berserker);
    }

    @Test
    void aClanLevelAddsUpEverythingUnlockedSoFar() {
        UUID clanId = UUID.randomUUID();
        when(clanRepository.findByIdAndActiveTrue(clanId))
                .thenReturn(Optional.of(Clan.builder().id(clanId).totalXp(350.0).build()));
        when(capacityRepository.findAll()).thenReturn(List.of(
                row(0, ClanCapacity.member_slots, 10),
                row(2, ClanCapacity.member_slots, 5),
                row(3, ClanCapacity.member_slots, 5)));

        assertThat(levelService.level(clanId).unlocked().capacities()).containsEntry(ClanCapacity.member_slots, 15);
    }

    @Test
    void aWarModeIsAvailableOnceTheClanReachesItsLevel() {
        when(warModeRepository.findById(new ClanLevelWarMode.Key(ClanWarModeAxis.ruleset, "berserker")))
                .thenReturn(Optional.of(ClanLevelWarMode.builder().axis(ClanWarModeAxis.ruleset).mode("berserker")
                        .level(2).build()));

        assertThat(levelService.hasWarMode(Clan.builder().totalXp(350.0).build(), ClanWarModeAxis.ruleset,
                "berserker")).isTrue();
        assertThat(levelService.hasWarMode(Clan.builder().totalXp(0.0).build(), ClanWarModeAxis.ruleset,
                "berserker")).isFalse();
        assertThat(levelService.hasWarMode(Clan.builder().totalXp(350.0).build(), ClanWarModeAxis.arena,
                "random")).isFalse();
    }

    @Nested
    class Admin {

        private final ItemType clanCosmetic = ItemType.builder().key("clan_cosmetic").build();

        @Test
        void aLevelItemGoesStraightToClansAlreadyPastThatLevel() {
            Item banner = Item.builder().id(UUID.randomUUID())
                    .type(ItemType.builder().key("clan_banner").parentType(clanCosmetic).build()).build();
            when(itemRepository.findById(banner.getId())).thenReturn(Optional.of(banner));
            when(levelItemRepository.findById(banner.getId())).thenReturn(Optional.empty());

            levelService.setLevelItem(2, banner.getId());

            ArgumentCaptor<ClanLevelItem> saved = ArgumentCaptor.forClass(ClanLevelItem.class);
            verify(levelItemRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getLevel()).isEqualTo(2);
            assertThat(saved.getValue().getItem()).isSameAs(banner);
            verify(clanItemRepository).grantToClansAtLevel(banner.getId(), 2, 300.0);
        }

        @Test
        void aPlayerItemCannotBeALevelReward() {
            Item crate = Item.builder().id(UUID.randomUUID()).type(ItemType.builder().key("crate").build()).build();
            when(itemRepository.findById(crate.getId())).thenReturn(Optional.of(crate));

            assertThatThrownBy(() -> levelService.setLevelItem(2, crate.getId()))
                    .isInstanceOf(ValidationException.class);
            verify(levelItemRepository, never()).saveAndFlush(any());
        }

        @Test
        void aWarModeHasToBelongToItsAxis() {
            assertThatThrownBy(() -> levelService.setWarMode(ClanWarModeAxis.arena, "berserker", 1))
                    .isInstanceOf(ValidationException.class);

            levelService.setWarMode(ClanWarModeAxis.ruleset, "berserker", 3);

            ArgumentCaptor<ClanLevelWarMode> saved = ArgumentCaptor.forClass(ClanLevelWarMode.class);
            verify(warModeRepository).save(saved.capture());
            assertThat(saved.getValue().getLevel()).isEqualTo(3);
        }
    }
}
