package com.accsaber.backend.service.milestone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.FilterSpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.SelectSpec;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSchemaResponse;
import com.accsaber.backend.service.milestone.source.ItemSources;
import com.accsaber.backend.service.milestone.source.MapSources;
import com.accsaber.backend.service.milestone.source.ProgressSources;
import com.accsaber.backend.service.milestone.source.MilestoneSourceRegistry;
import com.accsaber.backend.service.milestone.source.PlayerSources;
import com.accsaber.backend.service.milestone.source.ProgressionSources;
import com.accsaber.backend.service.milestone.source.ScoreSources;
import com.accsaber.backend.service.milestone.sql.MilestoneSqlCompiler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class MilestoneQueryBuilderServiceTest {

        private EntityManager entityManager;
        private MilestoneQueryBuilderService service;
        private Query mockQuery;

        @BeforeEach
        void setUp() {
                entityManager = mock(EntityManager.class);
                mockQuery = mock(Query.class);
                lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);

                MilestoneSourceRegistry registry = new MilestoneSourceRegistry(List.of(
                                new ScoreSources(), new MapSources(), new PlayerSources(), new ProgressionSources(),
                                new ItemSources(), new ProgressSources()));
                service = new MilestoneQueryBuilderService(entityManager, registry, new MilestoneSqlCompiler(registry));
        }

        private String capturedSql() {
                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(entityManager, atLeastOnce()).createNativeQuery(captor.capture());
                return captor.getValue();
        }

        private void assertRankedFilterApplied() {
                assertThat(String.join(" ", allCapturedSql())).contains("status = 'ranked'");
        }

        private List<String> allCapturedSql() {
                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(entityManager, atLeastOnce()).createNativeQuery(captor.capture());
                return captor.getAllValues();
        }

        @Nested
        class Validate {

                @Test
                void validSpec_passesWithoutException() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)));

                        service.validate(spec);
                }

                @Test
                void nullSpec_throwsValidationException() {
                        assertThatThrownBy(() -> service.validate(null))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("select and from");
                }

                @Test
                void countryScope_onTableWithoutCountry_throws() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"), "maps", null,
                                        null, null, null, null, null, null, null, "COUNTRY");

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("COUNTRY scope");
                }

                @Test
                void invalidScope_throws() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"), "scores", null,
                                        null, null, null, null, null, null, null, "REGION");

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported scope");
                }

                @Test
                void unknownTable_throwsValidationException() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "staff_users",
                                        null);

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported table");
                }

                @Test
                void unknownFunction_throwsValidationException() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("DELETE", "ap"),
                                        "scores",
                                        null);

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported function");
                }

                @Test
                void unknownSelectColumn_throwsValidationException() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "password"),
                                        "scores",
                                        null);

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported select column");
                }

                @Test
                void unknownFilterColumn_throwsValidationException() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("nonexistent_col", "=", 1)));

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported filter column");
                }

                @Test
                void unknownOperator_throwsValidationException() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("active", "LIKE", true)));

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported operator");
                }

                @Test
                void nullFilterValue_throwsValidationException() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", null)));

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Filter value must not be null");
                }

                @Test
                void crossTableColumn_isAllowed() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "accuracy"),
                                        "scores",
                                        List.of(new FilterSpec("map_difficulty_status", "=", "RANKED")));

                        service.validate(spec);
                }

                @Test
                void countDistinct_withEntityColumn_isAllowed() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT_DISTINCT", "map_difficulty_id"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)));

                        service.validate(spec);
                }

                @Test
                void inOperatorWithSubquery_isValid() {
                        MilestoneQuerySpec subquery = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "map_difficulty_uuid_id"),
                                        "map_difficulty_complexities",
                                        null);
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("map_difficulty_uuid_id", "IN", null, subquery)));

                        service.validate(spec);
                }

                @Test
                void inOperatorWithoutSubquery_throwsValidationException() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("map_difficulty_uuid_id", "IN", null)));

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("requires a subquery");
                }

                @Test
                void plainFunctionInTopLevel_throwsValidationException() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "ap"),
                                        "scores",
                                        null);

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("PLAIN function is only valid in subqueries");
                }

                @Test
                void nestedSubquery_isValid() {
                        MilestoneQuerySpec deepest = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "complexity"),
                                        "map_difficulty_complexities",
                                        null);
                        MilestoneQuerySpec middle = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "map_difficulty_uuid_id"),
                                        "map_difficulty_complexities",
                                        List.of(new FilterSpec("complexity", "=", null, deepest)));
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("map_difficulty_uuid_id", "IN", null, middle)));

                        service.validate(spec);
                }

                @Test
                void usersSubqueryWithNoFilters_isValid() {
                        MilestoneQuerySpec subquery = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "country"),
                                        "users",
                                        null);
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("user_country", "IN", null, subquery)));

                        service.validate(spec);
                }

                @Test
                void scalarOperatorWithSubquery_isValid() {
                        MilestoneQuerySpec subquery = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "complexity"),
                                        "map_difficulty_complexities",
                                        null);
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("ap", ">=", null, subquery)));

                        service.validate(spec);
                }
        }

        @Nested
        class Evaluate {

                static Stream<Arguments> aggregateSpecs() {
                        return Stream.of(
                                        Arguments.of("MAX", "ap", 123L, (double) (850), (double) (850),
                                                        List.of("MAX(s.ap)", "FROM scores s", "s.user_id = :userId",
                                                                        "md.status = 'ranked'", ":p0")),
                                        Arguments.of("COUNT_DISTINCT", "map_difficulty_id", 7L, 15L, (double) (15),
                                                        List.of("COUNT(DISTINCT s.map_difficulty_id)",
                                                                        "md.status = 'ranked'")),
                                        Arguments.of("SUM", "score", 1L, 8_500_000L, (double) (8_500_000),
                                                        List.of("SUM(s.score)", "md.status = 'ranked'")));
                }

                @ParameterizedTest(name = "{0}({1})")
                @MethodSource("aggregateSpecs")
                void aggregateOverActiveScores_buildsCorrectSql(String function, String column, Long userId,
                                Object rawResult, double expected, List<String> fragments) {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec(function, column),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)));

                        when(mockQuery.getSingleResult()).thenReturn(rawResult);

                        Double result = service.evaluate(spec, userId, null);

                        assertThat(result).isEqualByComparingTo(expected);

                        String sql = capturedSql();
                        assertThat(sql).contains(fragments);
                        assertRankedFilterApplied();
                }

                @Test
                void scoresTable_alwaysExcludesCampaignAttempts() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)));

                        when(mockQuery.getSingleResult()).thenReturn((double) (850));

                        service.evaluate(spec, 123L, null);

                        String sql = capturedSql();
                        assertThat(sql).contains(
                                        "(s.supersedes_reason IS NULL OR s.supersedes_reason <> 'Campaign attempt')");
                }

                @Test
                void nonScoreTable_doesNotGetCampaignAttemptExclusion() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "total_xp"),
                                        "users",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn(5000);

                        service.evaluate(spec, 999L, null);

                        String sql = capturedSql();
                        assertThat(sql).doesNotContain("Campaign attempt");
                }

                @Test
                void userIdIsInjectedForUserTable() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "total_xp"),
                                        "users",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn(5000);

                        service.evaluate(spec, 999L, null);

                        verify(mockQuery).setParameter("userId", 999L);
                }

                @Test
                void categoryIdIsInjectedWhenTableSupportsIt() {
                        UUID categoryId = UUID.randomUUID();
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("SUM", "ap"),
                                        "scores",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn((double) (3000));

                        service.evaluate(spec, 42L, categoryId);

                        verify(mockQuery).setParameter("userId", 42L);
                        verify(mockQuery).setParameter("categoryId", categoryId);
                        assertRankedFilterApplied();
                }

                @Test
                void categoryIdIsNotInjectedForTableWithNoPath() {
                        UUID categoryId = UUID.randomUUID();
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "total_xp"),
                                        "users",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn((double) (100));

                        service.evaluate(spec, 1L, categoryId);

                        String sql = capturedSql();
                        assertThat(sql).doesNotContain(":categoryId");
                        assertThat(sql).doesNotContain("'ranked'");
                }

                @Test
                void rankedStatusNotInjected_forUserCategoryStatisticsTable() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "user_category_statistics",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn((double) (3500));

                        service.evaluate(spec, 1L, null);

                        String sql = capturedSql();
                        assertThat(sql).doesNotContain("'ranked'");
                }

                @Test
                void nullResult_returnsNull() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn(null);

                        Double result = service.evaluate(spec, 1L, null);

                        assertThat(result).isNull();
                }

                @Test
                void selectOffset_appliedToAggregate() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MIN", "country_ranking", -1),
                                        "user_category_statistics",
                                        List.of(new FilterSpec("active", "=", true)));

                        when(mockQuery.getSingleResult()).thenReturn((double) (4));

                        Double result = service.evaluate(spec, 5L, null);

                        assertThat(result).isEqualByComparingTo((double) (4));
                        String sql = capturedSql();
                        assertThat(sql).contains("(MIN(ucs.country_ranking) - 1)");
                }

                @Test
                void countryScope_replacesUserScopeWithCountrySubquery() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "user_category_statistics",
                                        List.of(new FilterSpec("active", "=", true)),
                                        null, null, null, null, null, null, null, "COUNTRY");

                        when(mockQuery.getSingleResult()).thenReturn(50L);

                        service.evaluate(spec, 5L, null);

                        String sql = capturedSql();
                        assertThat(sql).contains(
                                        "usr.country = (SELECT country FROM users WHERE id = :userId)");
                        assertThat(sql).doesNotContain("ucs.user_id = :userId");
                        verify(mockQuery).setParameter("userId", 5L);
                }

                @Test
                void percentile_dividesRankByCountryPopulation() {
                        MilestoneQuerySpec divisor = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "user_category_statistics",
                                        List.of(new FilterSpec("active", "=", true)),
                                        null, null, null, null, null, null, null, "COUNTRY");
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MIN", "country_ranking", -1),
                                        "user_category_statistics",
                                        List.of(new FilterSpec("active", "=", true)),
                                        null, divisor, null, null, null, null);

                        when(mockQuery.getSingleResult())
                                        .thenReturn((double) (4), (double) (50));

                        Double result = service.evaluate(spec, 5L, null);

                        assertThat(result).isEqualByComparingTo(0.08);
                }

                @ParameterizedTest(name = "status filter value {0}")
                @ValueSource(strings = { "RANKED", "ranked" })
                void crossTableFilter_mapDifficultyStatus_bindsDbValue(String status) {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("map_difficulty_status", "=", status)));

                        when(mockQuery.getSingleResult()).thenReturn((double) (900));

                        service.evaluate(spec, 1L, null);

                        String sql = capturedSql();
                        assertThat(sql).contains("md.status");

                        verify(mockQuery).setParameter("p0", "ranked");
                        assertRankedFilterApplied();
                }

                @Test
                void crossTableFilter_difficulty_dbValueFallback() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("map_difficulty_difficulty", "=", "ExpertPlus")));

                        when(mockQuery.getSingleResult()).thenReturn(5L);

                        service.evaluate(spec, 1L, null);

                        verify(mockQuery).setParameter("p0", "ExpertPlus");
                        assertRankedFilterApplied();
                }

                @Test
                void crossTableSelect_accuracy_usesVirtualExpression() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "accuracy"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)));

                        when(mockQuery.getSingleResult()).thenReturn(0.99);

                        service.evaluate(spec, 1L, null);

                        String sql = capturedSql();
                        assertThat(sql)
                                        .contains("CAST(s.score AS DOUBLE PRECISION) / md.max_score");
                        assertThat(sql).contains("md.status = 'ranked'");
                        assertRankedFilterApplied();
                }

                @Test
                void crossTableFilter_songName_usesNavigationPath() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("song_name", "=", "Ghost")));

                        when(mockQuery.getSingleResult()).thenReturn(1L);

                        service.evaluate(spec, 1L, null);

                        String sql = capturedSql();
                        assertThat(sql).contains("mp.song_name");
                        assertThat(sql).contains("md.status = 'ranked'");
                        assertRankedFilterApplied();
                }

                @Test
                void coercion_integerValueForDoubleColumn() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("ap", ">=", 999)));

                        when(mockQuery.getSingleResult()).thenReturn(3L);

                        service.evaluate(spec, 1L, null);

                        verify(mockQuery).setParameter("p0", 999.0);
                        assertRankedFilterApplied();
                }

                @Test
                void coercion_wholeDoubleValueForIntegerColumn() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("streak_115", ">=", 1.0)));

                        when(mockQuery.getSingleResult()).thenReturn(2L);

                        service.evaluate(spec, 1L, null);

                        verify(mockQuery).setParameter("p0", 1);
                        assertRankedFilterApplied();
                }

                @Test
                void coercion_fractionalValueForIntegerColumn_throwsValidationException() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("streak_115", ">=", 1.5)));

                        assertThatThrownBy(() -> service.evaluate(spec, 1L, null))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("INTEGER");
                }

                @Test
                void multipleFilters_allBoundAsParameters() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(
                                                        new FilterSpec("active", "=", true),
                                                        new FilterSpec("misses", "=", 0),
                                                        new FilterSpec("bad_cuts", "=", 0)));

                        when(mockQuery.getSingleResult()).thenReturn((double) (750));

                        service.evaluate(spec, 1L, null);

                        verify(mockQuery).setParameter("p0", true);
                        verify(mockQuery).setParameter("p1", 0);
                        verify(mockQuery).setParameter("p2", 0);
                        assertRankedFilterApplied();
                }

                @Test
                void userCategoryStatistics_rankedPlays() {
                        UUID categoryId = UUID.randomUUID();
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ranked_plays"),
                                        "user_category_statistics",
                                        List.of(new FilterSpec("active", "=", true)));

                        when(mockQuery.getSingleResult()).thenReturn(73L);

                        service.evaluate(spec, 1L, categoryId);

                        String sql = capturedSql();
                        assertThat(sql).contains("MAX(ucs.ranked_plays)");
                        assertThat(sql).contains("ucs.user_id = :userId");
                        assertThat(sql).contains("ucs.category_id = :categoryId");
                }

                @Test
                void usersSubquery_autoInjectsUserId() {
                        MilestoneQuerySpec usersSubquery = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "country"),
                                        "users",
                                        null);
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("user_country", "IN", null, usersSubquery)));

                        when(mockQuery.getSingleResult()).thenReturn((double) (900));

                        service.evaluate(spec, 42L, null);

                        String sql = capturedSql();

                        assertThat(sql).contains("FROM users u_1");
                        assertThat(sql).contains("u_1.id = :userId");
                        assertThat(sql).doesNotContain(":p0");
                }

                @Test
                void bestScoreInCountryForMap_generatesCorrectSql() {
                        UUID mapDiffId = UUID.randomUUID();

                        MilestoneQuerySpec userCountrySubquery = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "country"),
                                        "users",
                                        null);

                        MilestoneQuerySpec bestCountryScoreSubquery = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(
                                                        new FilterSpec("map_difficulty_uuid_id", "=",
                                                                        mapDiffId.toString()),
                                                        new FilterSpec("active", "=", true),
                                                        new FilterSpec("user_country", "=", null,
                                                                        userCountrySubquery)));

                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(
                                                        new FilterSpec("map_difficulty_uuid_id", "=",
                                                                        mapDiffId.toString()),
                                                        new FilterSpec("ap", ">=", null,
                                                                        bestCountryScoreSubquery)));

                        when(mockQuery.getSingleResult()).thenReturn((double) (870));

                        service.evaluate(spec, 42L, null);

                        String sql = capturedSql();

                        assertThat(sql).contains("MAX(s.ap)");
                        assertThat(sql).contains("s.map_difficulty_id = :p0");
                        assertThat(sql).contains("s.ap >= (");
                        assertThat(sql).contains("FROM scores s_1");
                        assertThat(sql).contains("s_1.map_difficulty_id = :p1");
                        assertThat(sql).contains("s_1.active = :p2");
                        assertThat(sql).contains("usr_1.country = (");
                        assertThat(sql).contains("FROM users u_2");
                        assertThat(sql).contains("u_2.id = :userId");
                        assertThat(sql).contains("md_1.status = 'ranked'");
                        assertThat(sql).doesNotContain("s_1.user_id = :userId");

                        assertRankedFilterApplied();
                        verify(mockQuery).setParameter("userId", 42L);
                }

                @Test
                void singleLevelSubquery_generatesDepthSuffixedAlias() {
                        MilestoneQuerySpec subquery = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "map_difficulty_uuid_id"),
                                        "map_difficulty_complexities",
                                        List.of(new FilterSpec("complexity", ">=", 8.0)));
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(
                                                        new FilterSpec("active", "=", true),
                                                        new FilterSpec("map_difficulty_uuid_id", "IN", null,
                                                                        subquery)));

                        when(mockQuery.getSingleResult()).thenReturn((double) (750));

                        service.evaluate(spec, 5L, null);

                        String sql = capturedSql();

                        assertThat(sql).contains("s.map_difficulty_id IN (");
                        assertThat(sql).contains("FROM map_difficulty_complexities mdc_1");
                        assertThat(sql).contains("mdc_1.map_difficulty_id");
                        assertThat(sql).contains("md_1.status = 'ranked'");
                        assertThat(sql).doesNotContain(" mdc ");

                        verify(mockQuery).setParameter("p0", true);
                        verify(mockQuery).setParameter("p1", 8.0);
                        assertRankedFilterApplied();
                }

                @Test
                void nestedSubquery_fullComboOnHighestComplexityMap() {
                        MilestoneQuerySpec deepest = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "complexity"),
                                        "map_difficulty_complexities",
                                        null);
                        MilestoneQuerySpec middle = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "map_difficulty_uuid_id"),
                                        "map_difficulty_complexities",
                                        List.of(new FilterSpec("complexity", "=", null, deepest)));
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(
                                                        new FilterSpec("misses", "=", 0),
                                                        new FilterSpec("bad_cuts", "=", 0),
                                                        new FilterSpec("map_difficulty_uuid_id", "IN", null,
                                                                        middle)));

                        when(mockQuery.getSingleResult()).thenReturn((double) (980));

                        service.evaluate(spec, 42L, null);

                        String sql = capturedSql();

                        assertThat(sql).contains("MAX(s.ap)");
                        assertThat(sql).contains("s.misses = :p0");
                        assertThat(sql).contains("s.bad_cuts = :p1");
                        assertThat(sql).contains("s.map_difficulty_id IN (");
                        assertThat(sql).contains("FROM map_difficulty_complexities mdc_1");
                        assertThat(sql).contains("mdc_1.map_difficulty_id");
                        assertThat(sql).contains("mdc_1.complexity = (");
                        assertThat(sql).contains("FROM map_difficulty_complexities mdc_2");
                        assertThat(sql).contains("MAX(mdc_2.complexity)");
                        assertThat(sql).contains("md_1.status = 'ranked'");
                        assertThat(sql).contains("md_2.status = 'ranked'");
                        assertThat(sql).doesNotContain(" mdc ");

                        verify(mockQuery).setParameter("p0", 0);
                        verify(mockQuery).setParameter("p1", 0);
                        assertRankedFilterApplied();
                }

                @Test
                void userCategoryStatistics_crossTable_categoryCode() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "user_category_statistics",
                                        List.of(
                                                        new FilterSpec("active", "=", true),
                                                        new FilterSpec("category_code", "=", "true-acc")));

                        when(mockQuery.getSingleResult()).thenReturn(4200);

                        service.evaluate(spec, 5L, null);

                        String sql = capturedSql();
                        assertThat(sql).contains("cat.code");
                        verify(mockQuery).setParameter("p1", "true-acc");
                }
        }

        @Nested
        class GetSchema {

                @Test
                void exposesAllowedTablesAndTheirColumns() {
                        MilestoneSchemaResponse schema = service.getSchema();

                        assertThat(schema.tables()).containsKeys(
                                        "scores", "user_category_statistics", "users",
                                        "user_milestone_links", "maps", "map_difficulties",
                                        "map_difficulty_statistics", "map_difficulty_complexities",
                                        "categories", "modifiers", "milestones", "milestone_sets",
                                        "level_thresholds");

                        List<String> scoreColNames = schema.tables().get("scores").stream()
                                        .map(MilestoneSchemaResponse.ColumnInfo::name)
                                        .toList();

                        assertThat(scoreColNames).contains(
                                        "accuracy", "map_difficulty_status", "map_difficulty_difficulty",
                                        "song_name", "song_author", "map_author", "category_name", "category_code");
                        assertThat(scoreColNames).contains("supersedes_id", "supersedes_time_set");

                        assertThat(schema.tables()).containsKey("score_modifier_links");
                        List<String> colNames = schema.tables().get("score_modifier_links").stream()
                                        .map(MilestoneSchemaResponse.ColumnInfo::name)
                                        .toList();
                        assertThat(colNames).contains("id", "score_id", "modifier_id");
                }

                @Test
                void doesNotIncludeStaffTables() {
                        MilestoneSchemaResponse schema = service.getSchema();

                        assertThat(schema.tables()).doesNotContainKeys(
                                        "staff_users", "admin_actions", "staff_map_votes");
                }

                @Test
                void enumColumns_includeEnumValues() {
                        MilestoneSchemaResponse schema = service.getSchema();

                        MilestoneSchemaResponse.ColumnInfo statusCol = schema.tables()
                                        .get("scores").stream()
                                        .filter(c -> c.name().equals("map_difficulty_status"))
                                        .findFirst().orElseThrow();

                        assertThat(statusCol.type()).isEqualTo("enum");
                        assertThat(statusCol.enumValues()).containsExactlyInAnyOrder("QUEUE", "QUALIFIED", "RANKED",
                                        "CAMPAIGN");
                }

                @Test
                void functionsAndOperators_areIncluded() {
                        MilestoneSchemaResponse schema = service.getSchema();

                        assertThat(schema.functions()).containsExactlyInAnyOrder(
                                        "AVG", "COUNT", "COUNT_DISTINCT", "MAX", "MIN", "PLAIN", "SUM");
                        assertThat(schema.operators()).containsExactlyInAnyOrder(
                                        "!=", "<", "<=", "=", ">", ">=", "IS NULL", "IS NOT NULL");
                }
        }

        @Nested
        class ValidateHaving {

                @Test
                void validHaving_passesValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("AVG", "accuracy"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)),
                                        new MilestoneQuerySpec.HavingSpec("COUNT", "rank", ">=", 20),
                                        null, null, null, null, null);

                        service.validate(spec);
                }

                @Test
                void invalidHavingFunction_throwsValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("AVG", "accuracy"),
                                        "scores",
                                        null,
                                        new MilestoneQuerySpec.HavingSpec("DELETE", "id", ">=", 20),
                                        null, null, null, null, null);

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported having function");
                }

                @Test
                void invalidHavingColumn_throwsValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("AVG", "accuracy"),
                                        "scores",
                                        null,
                                        new MilestoneQuerySpec.HavingSpec("COUNT", "nonexistent", ">=", 20),
                                        null, null, null, null, null);

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported having column");
                }

                @Test
                void havingGeneratesCaseWhenJpql() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("AVG", "accuracy"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)),
                                        new MilestoneQuerySpec.HavingSpec("COUNT", "rank", ">=", 20),
                                        null, null, null, null, null);

                        when(mockQuery.getSingleResult()).thenReturn(0.95);

                        service.evaluate(spec, 1L, null);

                        String sql = capturedSql();
                        assertThat(sql).contains("CASE WHEN COUNT(s.rank) >=");
                        assertThat(sql).contains("THEN AVG(");
                        assertThat(sql).contains("ELSE NULL END");
                }
        }

        @Nested
        class ValidateDivisor {

                @Test
                void validDivisor_passesValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT_DISTINCT", "map_difficulty_id"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)),
                                        null,
                                        new MilestoneQuerySpec(
                                                        new SelectSpec("COUNT", "id"),
                                                        "map_difficulties",
                                                        List.of(new FilterSpec("active", "=", true))),
                                        null, null, null, null);

                        service.validate(spec);
                }

                @Test
                void divisorWithInvalidTable_throwsValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        null,
                                        null,
                                        new MilestoneQuerySpec(
                                                        new SelectSpec("COUNT", "id"),
                                                        "staff_users",
                                                        null),
                                        null, null, null, null);

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported table");
                }
        }

        @Nested
        class ValidateTransform {

                @Test
                void modTransform_passesValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("score", "=", 115, null,
                                                        new MilestoneQuerySpec.TransformSpec("MOD", 1000))));

                        service.validate(spec);
                }

                @Test
                void intervalSubtractTransform_passesValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("supersedes_time_set", "<", "2024-01-01T00:00:00Z", null,
                                                        new MilestoneQuerySpec.TransformSpec("INTERVAL_SUBTRACT",
                                                                        "90 days"))));

                        service.validate(spec);
                }

                @Test
                void unknownTransformFunction_throwsValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("score", "=", 1, null,
                                                        new MilestoneQuerySpec.TransformSpec("SQRT", 2))));

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported transform function");
                }

                @Test
                void modTransform_generatesCorrectSql() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("score", "=", 115, null,
                                                        new MilestoneQuerySpec.TransformSpec("MOD", 1000))));

                        when(mockQuery.getSingleResult()).thenReturn(1L);

                        service.evaluate(spec, 1L, null);

                        String sql = capturedSql();
                        assertThat(sql).contains("MOD(s.score, 1000)");
                }
        }

        @Nested
        class ValidateGroupBy {

                @Test
                void validGroupBy_passesValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)),
                                        null, null,
                                        List.of(new MilestoneQuerySpec.GroupBySpec("map_difficulty_id")),
                                        "MAX", null, null);

                        service.validate(spec);
                }

                @Test
                void groupByWithDateCast_passesValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT_DISTINCT", "map_difficulty_id"),
                                        "scores",
                                        null, null, null,
                                        List.of(new MilestoneQuerySpec.GroupBySpec("time_set", "DATE")),
                                        "MAX", null, null);

                        service.validate(spec);
                }

                @Test
                void groupByWithoutOuterFunction_throwsValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        null, null, null,
                                        List.of(new MilestoneQuerySpec.GroupBySpec("map_difficulty_id")),
                                        null, null, null);

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("outer_function is required");
                }

                @Test
                void groupByWithInvalidColumn_throwsValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        null, null, null,
                                        List.of(new MilestoneQuerySpec.GroupBySpec("nonexistent")),
                                        "MAX", null, null);

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported group_by column");
                }

                @Test
                void groupByWithInvalidCast_throwsValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        null, null, null,
                                        List.of(new MilestoneQuerySpec.GroupBySpec("time_set", "FLOAT")),
                                        "MAX", null, null);

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Unsupported group_by cast");
                }
        }

        @Nested
        class EnumFilters {

                @Test
                void lowercaseEnumConstant_bindsItsName() {
                        when(mockQuery.getSingleResult()).thenReturn(1L);
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "crate_opens",
                                        List.of(new FilterSpec("reward_item_rarity", "=", "legendary")));

                        service.evaluate(spec, 1L, null);

                        verify(mockQuery).setParameter("p0", "legendary");
                }

                @Test
                void dbValueEnumConstant_bindsItsDbValue() {
                        when(mockQuery.getSingleResult()).thenReturn(1L);
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "user_campaigns",
                                        List.of(new FilterSpec("status", "=", "COMPLETED")));

                        service.evaluate(spec, 1L, null);

                        verify(mockQuery).setParameter("p0", "completed");
                }

                @Test
                void unknownEnumValue_throwsValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "crate_opens",
                                        List.of(new FilterSpec("reward_item_rarity", "=", "shiny")));

                        assertThatThrownBy(() -> service.evaluate(spec, 1L, null))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Invalid value");
                }
        }

        @Nested
        class OuterReferences {

                @Test
                void outerRef_isValidatedAgainstTheParentSource() {
                        MilestoneQuerySpec inner = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "id"), "map_difficulties",
                                        List.of(new FilterSpec("batch_id", "=", null, null, null,
                                                        "OUTER.map_difficulty_batch_id", null)));
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"), "scores",
                                        List.of(new FilterSpec("id", "NOT EXISTS", null, inner)));

                        service.validate(spec);
                }

                @Test
                void outerRef_unknownOnParent_throwsValidation() {
                        MilestoneQuerySpec inner = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "id"), "map_difficulties",
                                        List.of(new FilterSpec("batch_id", "=", null, null, null,
                                                        "OUTER.nope", null)));
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"), "scores",
                                        List.of(new FilterSpec("id", "NOT EXISTS", null, inner)));

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("column_ref");
                }
        }

        @Nested
        class SubqueryUserScope {

                @Test
                void userScopedSubquery_getsTheUserFilter() {
                        when(mockQuery.getSingleResult()).thenReturn(1L);
                        MilestoneQuerySpec inner = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "id"), "scores",
                                        List.of(new FilterSpec("partial", "=", true)),
                                        null, null, null, null, null, null, null, "USER");
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"), "scores",
                                        List.of(new FilterSpec("id", "NOT EXISTS", null, inner)));

                        service.evaluate(spec, 1L, null);

                        assertThat(capturedSql()).contains("s_1.user_id = :userId");
                }

                @Test
                void plainSubquery_staysUncorrelated() {
                        when(mockQuery.getSingleResult()).thenReturn(1L);
                        MilestoneQuerySpec inner = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "id"), "scores",
                                        List.of(new FilterSpec("partial", "=", true)));
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"), "scores",
                                        List.of(new FilterSpec("id", "NOT EXISTS", null, inner)));

                        service.evaluate(spec, 1L, null);

                        assertThat(capturedSql()).doesNotContain("s_1.user_id = :userId");
                }
        }

        @Nested
        class ValidateNullOperators {

                @Test
                void isNotNullWithoutValue_passesValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("supersedes_id", "IS NOT NULL", null)));

                        service.validate(spec);
                }

                @Test
                void isNullWithValue_throwsValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("supersedes_id", "IS NULL", true)));

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("takes no value");
                }

                @Test
                void isNotNull_compilesWithoutBoundParameter() {
                        when(mockQuery.getSingleResult()).thenReturn(3L);
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("supersedes_id", "IS NOT NULL", null)));

                        service.evaluate(spec, 1L, null);

                        assertThat(capturedSql()).contains("s.supersedes_id IS NOT NULL");
                        verify(mockQuery, never()).setParameter(eq("p0"), any());
                }
        }

        @Nested
        class ValidateExistsOperator {

                @Test
                void notExistsWithSubquery_passesValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("id", "NOT EXISTS", null,
                                                        new MilestoneQuerySpec(
                                                                        new SelectSpec("COUNT", "id"),
                                                                        "scores",
                                                                        List.of(new FilterSpec("active", "=", true))))));

                        service.validate(spec);
                }

                @Test
                void notExistsWithoutSubquery_throwsValidation() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("id", "NOT EXISTS", null)));

                        assertThatThrownBy(() -> service.validate(spec))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("requires a subquery");
                }

                @Test
                void notExistsGeneratesCorrectJpql() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true),
                                                        new FilterSpec("id", "NOT EXISTS", null,
                                                                        new MilestoneQuerySpec(
                                                                                        new SelectSpec("COUNT", "id"),
                                                                                        "scores",
                                                                                        List.of(new FilterSpec("active",
                                                                                                        "=",
                                                                                                        true))))));

                        when(mockQuery.getSingleResult()).thenReturn(0L);

                        service.evaluate(spec, 1L, null);

                        String sql = capturedSql();
                        assertThat(sql).contains("NOT EXISTS (SELECT COUNT(s_1.id)");
                }
        }

}
