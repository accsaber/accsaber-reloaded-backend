package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.projection.ReweightRoundMapRow;
import com.accsaber.backend.model.dto.response.CategoryResponse;
import com.accsaber.backend.model.dto.response.map.ReweightDayResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexity;
import com.accsaber.backend.model.entity.map.ReweightRound;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;
import com.accsaber.backend.repository.map.ReweightRoundRepository;
import com.accsaber.backend.service.infra.CategoryService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService.RankedChange;

@ExtendWith(MockitoExtension.class)
class ReweightRoundServiceTest {

    @Mock
    private ReweightRoundRepository roundRepository;

    @Mock
    private MapDifficultyComplexityRepository complexityRepository;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private ReweightRoundService roundService;

    private final Category trueAcc = category("true_acc");
    private final Category techAcc = category("tech_acc");
    private final Category overall = category("overall");

    @Test
    void opensOneRoundPerCategoryWithBuffsAndNerfs() {
        Map<UUID, ReweightRound> rounds = roundService.open(List.of(
                change(trueAcc, 5.0, 6.0, "september"),
                change(trueAcc, 5.0, 4.0, "september"),
                change(trueAcc, 5.0, 5.0, "september"),
                change(techAcc, 7.0, 7.5, "tech fix")));

        ReweightRound trueRound = rounds.get(trueAcc.getId());
        assertThat(trueRound.getMapCount()).isEqualTo(3);
        assertThat(trueRound.getBuffs()).isEqualTo(1);
        assertThat(trueRound.getNerfs()).isEqualTo(1);
        assertThat(trueRound.getReason()).isEqualTo("september");
        assertThat(rounds.get(techAcc.getId()).getBuffs()).isEqualTo(1);
        verify(roundRepository).saveAll(rounds.values());
    }

    @Test
    void mixedReasonsLeaveTheRoundReasonEmpty() {
        ReweightRound round = roundService.open(List.of(
                change(trueAcc, 5.0, 6.0, "one"),
                change(trueAcc, 5.0, 6.0, "two"))).get(trueAcc.getId());

        assertThat(round.getReason()).isNull();
    }

    @Test
    void mergesEveryRoundOfOneDayIntoOneEntry() {
        Instant morning = Instant.parse("2026-09-18T09:00:00Z");
        ReweightRound byHand = round(trueAcc, 1, 1, 0, morning, "Downpour fix");
        ReweightRound script = round(techAcc, 188, 121, 67, morning.plusSeconds(3600), "September");
        ReweightRound nextDay = round(trueAcc, 40, 20, 20, Instant.parse("2026-09-19T01:00:00Z"), "Follow up");
        when(categoryService.findById(overall.getId())).thenReturn(response(overall));
        when(roundRepository.findLiveCategoriesOldestFirst()).thenReturn(List.of(byHand, script, nextDay));

        List<ReweightDayResponse> result = roundService.findForCategory(overall.getId());

        assertThat(result).hasSize(2);
        ReweightDayResponse first = result.getFirst();
        assertThat(first.getDay()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(first.getAt()).isEqualTo(morning);
        assertThat(first.getCategoryCodes()).containsExactly("true_acc", "tech_acc");
        assertThat(first.getMapCount()).isEqualTo(189);
        assertThat(first.getBuffs()).isEqualTo(122);
        assertThat(first.getNerfs()).isEqualTo(67);
        assertThat(first.getReason()).isNull();
        assertThat(first.getMaps()).isNull();
        assertThat(result.get(1).getReason()).isEqualTo("Follow up");
        verify(roundRepository, never()).findByCategoryOldestFirst(any());
        verify(complexityRepository, never()).findMapRowsByRoundIds(any());
    }

    @Test
    void smallDaysListEachMapOnceFromTheFirstValueToTheLast() {
        Instant morning = Instant.parse("2026-09-18T09:00:00Z");
        ReweightRound early = round(trueAcc, 1, 1, 0, morning, "fix");
        ReweightRound late = round(trueAcc, 2, 1, 1, morning.plusSeconds(600), "fix");
        UUID downpour = UUID.randomUUID();
        UUID fancy = UUID.randomUUID();
        when(categoryService.findById(trueAcc.getId())).thenReturn(response(trueAcc));
        when(roundRepository.findByCategoryOldestFirst(trueAcc.getId())).thenReturn(List.of(early, late));
        when(complexityRepository.findMapRowsByRoundIds(List.of(early.getId(), late.getId()))).thenReturn(List.of(
                row(early, downpour, "Downpour.vip", 1.0, 1.4),
                row(late, downpour, "Downpour.vip", 1.4, 1.3),
                row(late, fancy, "FANCY", 1.4, 1.8)));

        ReweightDayResponse day = roundService.findForCategory(trueAcc.getId()).getFirst();

        assertThat(day.getReason()).isEqualTo("fix");
        assertThat(day.getMaps()).extracting(ReweightDayResponse.MapChange::getSongName)
                .containsExactly("Downpour.vip", "FANCY");
        assertThat(day.getMaps().getFirst().getFrom()).isEqualTo(1.0);
        assertThat(day.getMaps().getFirst().getTo()).isEqualTo(1.3);
    }

    private static RankedChange change(Category category, double from, double to, String reason) {
        MapDifficulty difficulty = MapDifficulty.builder().id(UUID.randomUUID()).category(category).build();
        MapDifficultyComplexity current = MapDifficultyComplexity.builder().mapDifficulty(difficulty).complexity(from)
                .build();
        return new RankedChange(difficulty, current, to, reason);
    }

    private static CategoryResponse response(Category category) {
        return CategoryResponse.builder().id(category.getId()).code(category.getCode()).build();
    }

    private static Category category(String code) {
        return Category.builder().id(UUID.randomUUID()).code(code).build();
    }

    private static ReweightRound round(Category category, int mapCount, int buffs, int nerfs, Instant at,
            String reason) {
        return ReweightRound.builder().id(UUID.randomUUID()).category(category).mapCount(mapCount).buffs(buffs)
                .nerfs(nerfs).createdAt(at).reason(reason).build();
    }

    private static ReweightRoundMapRow row(ReweightRound round, UUID difficultyId, String song, double from,
            double to) {
        return new ReweightRoundMapRow(round.getId(), UUID.randomUUID(), difficultyId, song, Difficulty.EXPERT_PLUS,
                from, to);
    }
}
