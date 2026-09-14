package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.clan.ClanAllianceRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.service.score.APCalculationService;

@ExtendWith(MockitoExtension.class)
class ClanStrengthServiceTest {

    private static final UUID OVERALL = UUID.randomUUID();

    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanAllianceRepository allianceRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CurveRepository curveRepository;
    @Mock
    private APCalculationService apCalculationService;

    @InjectMocks
    private ClanStrengthService strengthService;

    private final Curve curve = Curve.builder().build();

    @BeforeEach
    void setUp() {
        lenient().when(categoryRepository.findByCodeAndActiveTrue("overall"))
                .thenReturn(Optional.of(Category.builder().id(OVERALL).build()));
        lenient().when(curveRepository.findById(UUID.fromString("acc00000-0000-0000-0000-000000000031")))
                .thenReturn(Optional.of(curve));
        lenient().when(apCalculationService.calculateWeightedAP(anyDouble(), anyInt(), eq(curve)))
                .thenAnswer(inv -> (double) inv.getArgument(0) / (1 + (int) inv.getArgument(1)));
    }

    private ClanMemberRepository.ClanSkillView skill(UUID clanId, double value) {
        return new ClanMemberRepository.ClanSkillView() {
            public UUID getClanId() {
                return clanId;
            }

            public double getSkill() {
                return value;
            }
        };
    }

    @Test
    void theRosterIsWeightedStrongestFirstAndAlliesTheSameWay() {
        UUID clanId = UUID.randomUUID();
        Clan clan = Clan.builder().id(clanId).build();
        when(memberRepository.findOpenMemberSkills(List.of(clanId), OVERALL))
                .thenReturn(List.of(skill(clanId, 20.0), skill(clanId, 60.0), skill(clanId, 30.0)));
        when(memberRepository.findFoughtAllyTopSkills(eq(List.of(clanId)), eq(OVERALL), any()))
                .thenReturn(List.of(skill(clanId, 40.0), skill(clanId, 80.0)));
        when(clanRepository.findAllById(List.of(clanId))).thenReturn(List.of(clan));

        strengthService.recompute(List.of(clanId));

        assertThat(clan.getRosterStrength()).isEqualTo(60.0 + 30.0 / 2 + 20.0 / 3);
        assertThat(clan.getAllyStrength()).isEqualTo(80.0 + 40.0 / 2);
    }

    @Test
    void aClanWithNobodyRatedHasNoStrength() {
        UUID clanId = UUID.randomUUID();
        Clan clan = Clan.builder().id(clanId).rosterStrength(99.0).allyStrength(5.0).build();
        when(clanRepository.findAllById(List.of(clanId))).thenReturn(List.of(clan));

        strengthService.recompute(List.of(clanId));

        assertThat(clan.getRosterStrength()).isZero();
        assertThat(clan.getAllyStrength()).isZero();
    }

    @Test
    void nothingToRecomputeTouchesNothing() {
        strengthService.recompute(List.of());

        verifyNoInteractions(memberRepository, clanRepository);
    }

    @Test
    void aMembershipChangeRecomputesThatClanAndItsAllies() {
        UUID clanId = UUID.randomUUID();
        UUID allyId = UUID.randomUUID();
        Clan clan = Clan.builder().id(clanId).build();
        Clan ally = Clan.builder().id(allyId).build();
        List<UUID> both = List.of(clanId, allyId);
        when(allianceRepository.findActiveAllyIds(List.of(clanId))).thenReturn(List.of(allyId));
        when(clanRepository.findAllById(both)).thenReturn(List.of(clan, ally));
        when(memberRepository.findOpenMemberSkills(both, OVERALL)).thenReturn(List.of(skill(clanId, 50.0)));
        when(memberRepository.findFoughtAllyTopSkills(eq(both), eq(OVERALL), any())).thenReturn(List.of(skill(allyId, 50.0)));

        strengthService.onMembershipChanged(new ClanMembershipChangedEvent(clanId));

        assertThat(clan.getRosterStrength()).isEqualTo(50.0);
        assertThat(ally.getAllyStrength()).isEqualTo(50.0);
    }
}
