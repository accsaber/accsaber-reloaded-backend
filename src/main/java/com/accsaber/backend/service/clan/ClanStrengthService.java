package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.clan.ClanAllianceRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.service.score.APCalculationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClanStrengthService {

    private static final UUID ROSTER_WEIGHT_CURVE_ID = UUID.fromString("acc00000-0000-0000-0000-000000000031");
    private static final String OVERALL_CODE = "overall";

    private final ClanRepository clanRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanAllianceRepository allianceRepository;
    private final CategoryRepository categoryRepository;
    private final CurveRepository curveRepository;
    private final APCalculationService apCalculationService;

    @Transactional
    public void recompute(Collection<UUID> clanIds) {
        if (clanIds.isEmpty()) {
            return;
        }
        UUID overallId = categoryRepository.findByCodeAndActiveTrue(OVERALL_CODE)
                .orElseThrow(() -> new IllegalStateException("Overall category not found")).getId();
        Curve curve = curveRepository.findById(ROSTER_WEIGHT_CURVE_ID)
                .orElseThrow(() -> new IllegalStateException("Clan roster weight curve not found"));
        Map<UUID, List<Double>> rosters = skillsByClan(memberRepository.findOpenMemberSkills(clanIds, overallId));
        Map<UUID, List<Double>> allies = skillsByClan(
                memberRepository.findFoughtAllyTopSkills(clanIds, overallId, Instant.now()));
        for (Clan clan : clanRepository.findAllById(clanIds)) {
            clan.setRosterStrength(weightedSum(rosters.get(clan.getId()), curve));
            clan.setAllyStrength(weightedSum(allies.get(clan.getId()), curve));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMembershipChanged(ClanMembershipChangedEvent event) {
        List<UUID> changed = List.of(event.clanId());
        recompute(Stream.concat(changed.stream(), allianceRepository.findActiveAllyIds(changed).stream()).toList());
    }

    private Map<UUID, List<Double>> skillsByClan(List<ClanMemberRepository.ClanSkillView> rows) {
        return rows.stream().collect(Collectors.groupingBy(ClanMemberRepository.ClanSkillView::getClanId,
                Collectors.mapping(ClanMemberRepository.ClanSkillView::getSkill, Collectors.toList())));
    }

    private double weightedSum(List<Double> skills, Curve curve) {
        if (skills == null) {
            return 0.0;
        }
        List<Double> ordered = skills.stream().sorted(Comparator.reverseOrder()).toList();
        double total = 0.0;
        for (int position = 0; position < ordered.size(); position++) {
            total += apCalculationService.calculateWeightedAP(ordered.get(position), position, curve);
        }
        return total;
    }
}
