package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.util.ArrayList;
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

    public record MemberStrength(Long userId, double skill, double share) {
    }

    @Transactional
    public void recompute(Collection<UUID> clanIds) {
        if (clanIds.isEmpty()) {
            return;
        }
        UUID overallId = overallId();
        Curve curve = rosterCurve();
        Map<UUID, List<Double>> rosters = skillsByClan(memberRepository.findOpenMemberSkills(clanIds, overallId));
        Map<UUID, List<Double>> allies = skillsByClan(
                memberRepository.findFoughtAllyTopSkills(clanIds, overallId, Instant.now()));
        for (Clan clan : clanRepository.findAllById(clanIds)) {
            clan.setRosterStrength(weightedSum(rosters.get(clan.getId()), curve));
            clan.setAllyStrength(weightedSum(allies.get(clan.getId()), curve));
        }
    }

    @Transactional(readOnly = true)
    public List<MemberStrength> memberStrengths(UUID clanId) {
        List<ClanMemberRepository.MemberSkillView> rows = memberRepository.findOpenMemberSkillsByClan(clanId,
                overallId()).stream()
                .sorted(Comparator.comparingDouble(ClanMemberRepository.MemberSkillView::getSkill).reversed()
                        .thenComparing(ClanMemberRepository.MemberSkillView::getUserId))
                .toList();
        List<Double> weights = weights(rows.stream().map(ClanMemberRepository.MemberSkillView::getSkill).toList(),
                rosterCurve());
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        List<MemberStrength> strengths = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            strengths.add(new MemberStrength(rows.get(i).getUserId(), rows.get(i).getSkill(),
                    total > 0 ? weights.get(i) / total : 0.0));
        }
        return strengths;
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
        return weights(skills.stream().sorted(Comparator.reverseOrder()).toList(), curve).stream()
                .mapToDouble(Double::doubleValue).sum();
    }

    private List<Double> weights(List<Double> orderedSkills, Curve curve) {
        List<Double> weights = new ArrayList<>(orderedSkills.size());
        for (int position = 0; position < orderedSkills.size(); position++) {
            weights.add(apCalculationService.calculateWeightedAP(orderedSkills.get(position), position, curve));
        }
        return weights;
    }

    private UUID overallId() {
        return categoryRepository.findByCodeAndActiveTrue(OVERALL_CODE)
                .orElseThrow(() -> new IllegalStateException("Overall category not found")).getId();
    }

    private Curve rosterCurve() {
        return curveRepository.findById(ROSTER_WEIGHT_CURVE_ID)
                .orElseThrow(() -> new IllegalStateException("Clan roster weight curve not found"));
    }
}
