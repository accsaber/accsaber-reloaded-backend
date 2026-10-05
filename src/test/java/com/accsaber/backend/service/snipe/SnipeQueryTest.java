package com.accsaber.backend.service.snipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort;

import com.accsaber.backend.model.entity.score.SnipeSort;
import com.accsaber.backend.model.entity.score.SnipeUnplayed;

class SnipeQueryTest {

    private static final Long SNIPER_ID = 76561198000000001L;
    private static final Long TARGET_ID = 76561198000000002L;

    @Nested
    class Defaults {

        @Test
        void missingSortFallsBackToClosestGapAscending() {
            SnipeQuery query = query(null, null);

            assertThat(query.sort()).isEqualTo(SnipeSort.GAP);
            assertThat(query.direction()).isEqualTo(Sort.Direction.ASC);
            assertThat(query.isDefaultOrder()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(SnipeSort.class)
        void eachSortPicksItsOwnNaturalDirection(SnipeSort sort) {
            SnipeQuery query = query(sort, null);

            assertThat(query.direction().isDescending()).isEqualTo(sort.isDescendingByDefault());
        }

        @Test
        void explicitDirectionWins() {
            assertThat(query(SnipeSort.AP_GAP, Sort.Direction.ASC).direction()).isEqualTo(Sort.Direction.ASC);
            assertThat(query(SnipeSort.GAP, Sort.Direction.DESC).direction()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        void flippedGapIsNoLongerTheDefaultOrder() {
            assertThat(query(SnipeSort.GAP, Sort.Direction.DESC).isDefaultOrder()).isFalse();
            assertThat(query(SnipeSort.TARGET_AP, null).isDefaultOrder()).isFalse();
        }
    }

    @Nested
    class ToSort {

        @ParameterizedTest
        @EnumSource(SnipeSort.class)
        void primaryOrderCarriesTheExpressionAndTiebreakerComesLast(SnipeSort sort) {
            List<Sort.Order> orders = query(sort, null).toSort().toList();

            assertThat(orders).hasSize(2);
            assertThat(orders.get(0).getProperty()).isEqualTo(sort.getExpression());
            assertThat(orders.get(0).getDirection().isDescending()).isEqualTo(sort.isDescendingByDefault());
            assertThat(orders.get(1).getProperty()).isEqualTo("s_b.id");
            assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.ASC);
        }

        @ParameterizedTest
        @EnumSource(SnipeSort.class)
        void everySortPushesEmptyValuesToTheEnd(SnipeSort sort) {
            Sort.Order primary = query(sort, null).toSort().toList().get(0);

            assertThat(primary.getNullHandling()).isEqualTo(Sort.NullHandling.NULLS_LAST);
        }

        @ParameterizedTest
        @EnumSource(SnipeSort.class)
        void everyExpressionSurvivesAliasPrefixing(SnipeSort sort) {
            String expression = sort.getExpression();

            assertThat(expression.startsWith("(") || expression.startsWith("s_a.") || expression.startsWith("s_b."))
                    .isTrue();
        }
    }

    @Nested
    class Unplayed {

        @ParameterizedTest(name = "toggle {0} resolves to {1}: played {3}, unplayed {4}")
        @CsvSource({
                ", EXCLUDE, true, true, false",
                "INCLUDE, INCLUDE, false, true, true",
                "ONLY, ONLY, false, false, true"
        })
        void toggleDecidesWhichSidesOfTheBoardSurvive(SnipeUnplayed requested, SnipeUnplayed expected,
                boolean isDefault, boolean allowsPlayed, boolean allowsUnplayed) {
            SnipeQuery query = new SnipeQuery(SNIPER_ID, TARGET_ID, null, null, null, requested);

            assertThat(query.unplayed()).isEqualTo(expected);
            assertThat(query.unplayed().isDefault()).isEqualTo(isDefault);
            assertThat(query.unplayed().allowsPlayed()).isEqualTo(allowsPlayed);
            assertThat(query.unplayed().allowsUnplayed()).isEqualTo(allowsUnplayed);
        }

        @Test
        void labelAndSlugDescribeTheToggle() {
            assertThat(SnipeUnplayed.ONLY.getLabel()).isEqualTo("unplayed only");
            assertThat(SnipeUnplayed.ONLY.getSlug()).isEqualTo("unplayed-only");
            assertThat(SnipeUnplayed.INCLUDE.getSlug()).isEqualTo("unplayed-include");
        }
    }

    @Nested
    class Labels {

        @Test
        void slugAndLabelDescribeTheChosenOrder() {
            SnipeQuery query = query(SnipeSort.RANK_GAP, Sort.Direction.ASC);

            assertThat(query.orderSlug()).isEqualTo("rank-gap-asc");
            assertThat(query.orderLabel()).isEqualTo("rank gap low to high");
        }
    }

    private SnipeQuery query(SnipeSort sort, Sort.Direction direction) {
        return new SnipeQuery(SNIPER_ID, TARGET_ID, null, sort, direction, null);
    }
}
