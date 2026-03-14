package com.accsaber.backend.service.milestone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.entity.milestone.LevelThreshold;
import com.accsaber.backend.repository.milestone.LevelThresholdRepository;

@ExtendWith(MockitoExtension.class)
class LevelServiceTest {

    @Mock
    private LevelThresholdRepository levelThresholdRepository;

    @InjectMocks
    private LevelService service;

    @Nested
    class XpForLevel {

        @Test
        void level1_returnsExpectedValue() {
            // floor(30 * 1^1.15) = floor(30) = 30
            assertThat(service.xpForLevel(1)).isEqualByComparingTo(BigDecimal.valueOf(30));
        }

        @Test
        void level2_returnsExpectedValue() {
            // floor(30 * 2^1.15) = floor(30 * 2.2239) = floor(66.71) = 66
            assertThat(service.xpForLevel(2).intValue()).isEqualTo(66);
        }

        @Test
        void level10_returnsExpectedValue() {
            // floor(30 * 10^1.15) = floor(30 * 14.125) = floor(423.7) = 423
            assertThat(service.xpForLevel(10).intValue()).isEqualTo(423);
        }

        @Test
        void level100_returnsCapValue() {
            BigDecimal level100Cost = service.xpForLevel(100);
            // floor(30 * 100^1.15) = floor(30 * 199.52) ≈ 5985
            assertThat(level100Cost.intValue()).isEqualTo(5985);
        }

        @Test
        void level101_sameAsLevel100_flatCap() {
            assertThat(service.xpForLevel(101)).isEqualByComparingTo(service.xpForLevel(100));
        }

        @Test
        void level200_sameAsLevel100_flatCap() {
            assertThat(service.xpForLevel(200)).isEqualByComparingTo(service.xpForLevel(100));
        }

        @Test
        void level999_sameAsLevel100_flatCap() {
            assertThat(service.xpForLevel(999)).isEqualByComparingTo(service.xpForLevel(100));
        }
    }

    @Nested
    class CalculateLevel {

        @Test
        void zeroXp_returnsLevel0() {
            LevelResponse response = service.calculateLevel(BigDecimal.ZERO);

            assertThat(response.getLevel()).isEqualTo(0);
            assertThat(response.getTotalXp()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getProgressPercent()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getTitle()).isNull();
        }

        @Test
        void nullXp_returnsLevel0() {
            LevelResponse response = service.calculateLevel(null);

            assertThat(response.getLevel()).isEqualTo(0);
        }

        @Test
        void exactlyEnoughForLevel1_returnsLevel1() {
            BigDecimal totalXp = BigDecimal.valueOf(30);
            when(levelThresholdRepository.findHighestTitleAtOrBelow(1))
                    .thenReturn(Optional.of(LevelThreshold.builder().level(1).title("Newcomer").build()));

            LevelResponse response = service.calculateLevel(totalXp);

            assertThat(response.getLevel()).isEqualTo(1);
            assertThat(response.getTitle()).isEqualTo("Newcomer");
            assertThat(response.getXpForCurrentLevel()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void justUnderLevel1_returnsLevel0() {
            BigDecimal totalXp = BigDecimal.valueOf(29);
            when(levelThresholdRepository.findHighestTitleAtOrBelow(0))
                    .thenReturn(Optional.empty());

            LevelResponse response = service.calculateLevel(totalXp);

            assertThat(response.getLevel()).isEqualTo(0);
        }

        @Test
        void progressPercent_isCalculatedCorrectly() {
            // level1 = 30, level2 = 66 → at 30 + 33 = 63 → 33/66 = 50%
            BigDecimal totalXp = BigDecimal.valueOf(63);
            when(levelThresholdRepository.findHighestTitleAtOrBelow(anyInt()))
                    .thenReturn(Optional.empty());

            LevelResponse response = service.calculateLevel(totalXp);

            assertThat(response.getLevel()).isEqualTo(1);
            assertThat(response.getProgressPercent().doubleValue()).isCloseTo(50.0, within(1.0));
        }

        @Test
        void xpForNextLevel_reportedCorrectly() {
            BigDecimal totalXp = BigDecimal.valueOf(30);
            when(levelThresholdRepository.findHighestTitleAtOrBelow(anyInt()))
                    .thenReturn(Optional.empty());

            LevelResponse response = service.calculateLevel(totalXp);
            assertThat(response.getXpForNextLevel()).isEqualByComparingTo(service.xpForLevel(2));
        }

        @Test
        void highLevel_titleFromHighestMatchingThreshold() {
            BigDecimal bigXp = BigDecimal.valueOf(5_000_000);
            LevelThreshold grandmaster = LevelThreshold.builder()
                    .level(30).title("Grandmaster").build();
            when(levelThresholdRepository.findHighestTitleAtOrBelow(anyInt()))
                    .thenReturn(Optional.of(grandmaster));

            LevelResponse response = service.calculateLevel(bigXp);

            assertThat(response.getLevel()).isGreaterThan(30);
            assertThat(response.getTitle()).isEqualTo("Grandmaster");
        }

        @Test
        void infiniteLevels_noCapOnLevel() {
            BigDecimal massiveXp = BigDecimal.valueOf(1_000_000_000);
            when(levelThresholdRepository.findHighestTitleAtOrBelow(anyInt()))
                    .thenReturn(Optional.empty());

            LevelResponse response = service.calculateLevel(massiveXp);

            assertThat(response.getLevel()).isGreaterThan(100);
        }

        @Test
        void aboveLevel100_xpForNextUsesLevel100Cost() {
            BigDecimal level100Cost = service.xpForLevel(100);

            BigDecimal cumulativeFor101 = BigDecimal.ZERO;
            for (int i = 1; i <= 101; i++) {
                cumulativeFor101 = cumulativeFor101.add(service.xpForLevel(i));
            }

            when(levelThresholdRepository.findHighestTitleAtOrBelow(anyInt()))
                    .thenReturn(Optional.empty());

            LevelResponse response = service.calculateLevel(cumulativeFor101);

            assertThat(response.getLevel()).isEqualTo(101);
            assertThat(response.getXpForNextLevel()).isEqualByComparingTo(level100Cost);
        }

        @Test
        void totalXp_setInResponse() {
            BigDecimal totalXp = BigDecimal.valueOf(500);
            when(levelThresholdRepository.findHighestTitleAtOrBelow(anyInt()))
                    .thenReturn(Optional.empty());

            LevelResponse response = service.calculateLevel(totalXp);

            assertThat(response.getTotalXp()).isEqualByComparingTo(totalXp);
        }
    }
}
