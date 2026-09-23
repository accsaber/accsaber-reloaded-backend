package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import com.accsaber.backend.model.dto.response.map.ReweightRoundResponse;
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
    void listsMapsOnlyForRoundsOfFiveOrFewer() {
        ReweightRound small = round(trueAcc, 2);
        ReweightRound large = round(trueAcc, 40);
        when(categoryService.findById(trueAcc.getId())).thenReturn(response(trueAcc));
        when(roundRepository.findByCategoryOldestFirst(trueAcc.getId())).thenReturn(List.of(small, large));
        when(complexityRepository.findMapRowsByRoundIds(List.of(small.getId()))).thenReturn(List.of(
                new ReweightRoundMapRow(small.getId(), UUID.randomUUID(), UUID.randomUUID(), "Liar Liar",
                        Difficulty.EXPERT_PLUS, 6.0, 5.4)));

        List<ReweightRoundResponse> result = roundService.findForCategory(trueAcc.getId());

        assertThat(result).extracting(ReweightRoundResponse::getId).containsExactly(small.getId(), large.getId());
        assertThat(result.get(0).getMaps()).singleElement()
                .satisfies(m -> {
                    assertThat(m.getSongName()).isEqualTo("Liar Liar");
                    assertThat(m.getFrom()).isEqualTo(6.0);
                    assertThat(m.getTo()).isEqualTo(5.4);
                });
        assertThat(result.get(1).getMaps()).isNull();
        assertThat(result.get(1).getCategoryCode()).isEqualTo("true_acc");
    }

    @Test
    void overallReadsEveryLiveCategory() {
        Category overall = category("overall");
        ReweightRound large = round(techAcc, 30);
        when(categoryService.findById(overall.getId())).thenReturn(response(overall));
        when(roundRepository.findLiveCategoriesOldestFirst()).thenReturn(List.of(large));

        List<ReweightRoundResponse> result = roundService.findForCategory(overall.getId());

        assertThat(result).singleElement().extracting(ReweightRoundResponse::getCategoryCode).isEqualTo("tech_acc");
        verify(roundRepository, never()).findByCategoryOldestFirst(any());
        verify(complexityRepository, never()).findMapRowsByRoundIds(any());
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

    private static ReweightRound round(Category category, int mapCount) {
        return ReweightRound.builder().id(UUID.randomUUID()).category(category).mapCount(mapCount)
                .createdAt(Instant.now()).build();
    }
}
