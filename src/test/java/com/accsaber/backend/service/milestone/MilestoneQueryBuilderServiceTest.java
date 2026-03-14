package com.accsaber.backend.service.milestone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.FilterSpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.SelectSpec;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSchemaResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class MilestoneQueryBuilderServiceTest {

        @Mock
        private EntityManager entityManager;

        @InjectMocks
        private MilestoneQueryBuilderService service;

        private Query mockQuery;

        @BeforeEach
        void setUp() {
                mockQuery = mock(Query.class);
                lenient().when(entityManager.createQuery(anyString())).thenReturn(mockQuery);
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
                void plainFunctionInSubquery_isValid() {
                        MilestoneQuerySpec inner = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "map_difficulty_uuid_id"),
                                        "map_difficulty_complexities",
                                        null);
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("map_difficulty_uuid_id", "IN", null, inner)));

                        service.validate(spec);
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

                @Test
                void simpleMaxAp_buildsCorrectJpql() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)));

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(850));

                        BigDecimal result = service.evaluate(spec, 123L, null);

                        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(850));

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        String jpql = jpqlCaptor.getValue();
                        assertThat(jpql).contains("MAX(s.ap)");
                        assertThat(jpql).contains("FROM Score s");
                        assertThat(jpql).contains("s.user.id = :userId");
                        assertThat(jpql).contains("s.mapDifficulty.status = :rankedStatus");
                        assertThat(jpql).contains(":p0");
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void userIdIsInjectedForUserTable() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "total_xp"),
                                        "users",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn(new BigDecimal("5000"));

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

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(3000));

                        service.evaluate(spec, 42L, categoryId);

                        verify(mockQuery).setParameter("userId", 42L);
                        verify(mockQuery).setParameter("categoryId", categoryId);
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void categoryIdIsNotInjectedForTableWithNoPath() {
                        UUID categoryId = UUID.randomUUID();
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "total_xp"),
                                        "users",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(100));

                        service.evaluate(spec, 1L, categoryId);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue()).doesNotContain(":categoryId");
                        assertThat(jpqlCaptor.getValue()).doesNotContain(":rankedStatus");
                }

                @Test
                void rankedStatusAutoInjected_forScoresTable() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.ZERO);

                        service.evaluate(spec, 1L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue()).contains("s.mapDifficulty.status = :rankedStatus");
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void rankedStatusNotInjected_forUsersTable() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "total_xp"),
                                        "users",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.ZERO);

                        service.evaluate(spec, 1L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue()).doesNotContain(":rankedStatus");
                }

                @Test
                void rankedStatusNotInjected_forUserCategoryStatisticsTable() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "user_category_statistics",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(3500));

                        service.evaluate(spec, 1L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue()).doesNotContain(":rankedStatus");
                }

                @Test
                void nullResult_returnsBigDecimalZero() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn(null);

                        BigDecimal result = service.evaluate(spec, 1L, null);

                        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
                }

                @Test
                void longResult_isConvertedToBigDecimal() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        null);

                        when(mockQuery.getSingleResult()).thenReturn(42L);

                        BigDecimal result = service.evaluate(spec, 1L, null);

                        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(42));
                }

                @Test
                void countDistinct_generatesCorrectJpql() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT_DISTINCT", "map_difficulty_id"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)));

                        when(mockQuery.getSingleResult()).thenReturn(15L);

                        service.evaluate(spec, 7L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue()).contains("COUNT(DISTINCT s.mapDifficulty)");
                        assertThat(jpqlCaptor.getValue()).contains("s.mapDifficulty.status = :rankedStatus");
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void crossTableFilter_mapDifficultyStatus_coercesStringToEnum() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("map_difficulty_status", "=", "RANKED")));

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(900));

                        service.evaluate(spec, 1L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue()).contains("s.mapDifficulty.status");

                        verify(mockQuery).setParameter("p0", MapDifficultyStatus.RANKED);
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void crossTableFilter_mapDifficultyStatus_dbValueFallback() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(new FilterSpec("map_difficulty_status", "=", "ranked")));

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.ZERO);

                        service.evaluate(spec, 1L, null);

                        verify(mockQuery).setParameter("p0", MapDifficultyStatus.RANKED);
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void crossTableFilter_difficulty_dbValueFallback() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("map_difficulty_difficulty", "=", "ExpertPlus")));

                        when(mockQuery.getSingleResult()).thenReturn(5L);

                        service.evaluate(spec, 1L, null);

                        verify(mockQuery).setParameter("p0", Difficulty.EXPERT_PLUS);
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void crossTableSelect_accuracy_usesVirtualExpression() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "accuracy"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)));

                        when(mockQuery.getSingleResult()).thenReturn(0.99);

                        service.evaluate(spec, 1L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue())
                                        .contains("CAST(s.score AS Double) / s.mapDifficulty.maxScore");
                        assertThat(jpqlCaptor.getValue()).contains("s.mapDifficulty.status = :rankedStatus");
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void crossTableFilter_songName_usesNavigationPath() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("song_name", "=", "Ghost")));

                        when(mockQuery.getSingleResult()).thenReturn(1L);

                        service.evaluate(spec, 1L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue()).contains("s.mapDifficulty.map.songName");
                        assertThat(jpqlCaptor.getValue()).contains("s.mapDifficulty.status = :rankedStatus");
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void coercion_integerValueForBigDecimalColumn() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("COUNT", "id"),
                                        "scores",
                                        List.of(new FilterSpec("ap", ">=", 999)));

                        when(mockQuery.getSingleResult()).thenReturn(3L);

                        service.evaluate(spec, 1L, null);

                        verify(mockQuery).setParameter("p0", new BigDecimal("999"));
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
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

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(750));

                        service.evaluate(spec, 1L, null);

                        verify(mockQuery).setParameter("p0", true);
                        verify(mockQuery).setParameter("p1", 0);
                        verify(mockQuery).setParameter("p2", 0);
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void sumTotalScoreAcrossUserScores() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("SUM", "score"),
                                        "scores",
                                        List.of(new FilterSpec("active", "=", true)));

                        when(mockQuery.getSingleResult()).thenReturn(8_500_000L);

                        BigDecimal result = service.evaluate(spec, 1L, null);

                        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(8_500_000));

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue()).contains("SUM(s.score)");
                        assertThat(jpqlCaptor.getValue()).contains("s.mapDifficulty.status = :rankedStatus");
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
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

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue()).contains("MAX(ucs.rankedPlays)");
                        assertThat(jpqlCaptor.getValue()).contains("ucs.user.id = :userId");
                        assertThat(jpqlCaptor.getValue()).contains("ucs.category.id = :categoryId");
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

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(900));

                        service.evaluate(spec, 42L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        String jpql = jpqlCaptor.getValue();

                        assertThat(jpql).contains("FROM User u_1");
                        assertThat(jpql).contains("u_1.id = :userId");
                        assertThat(jpql).doesNotContain(":p0");
                }

                @Test
                void bestScoreInCountryForMap_generatesCorrectJpql() {
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

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(870));

                        service.evaluate(spec, 42L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        String jpql = jpqlCaptor.getValue();

                        assertThat(jpql).contains("MAX(s.ap)");
                        assertThat(jpql).contains("s.mapDifficulty.id = :p0");
                        assertThat(jpql).contains("s.ap >= (");
                        assertThat(jpql).contains("FROM Score s_1");
                        assertThat(jpql).contains("s_1.mapDifficulty.id = :p1");
                        assertThat(jpql).contains("s_1.active = :p2");
                        assertThat(jpql).contains("s_1.user.country = (");
                        assertThat(jpql).contains("FROM User u_2");
                        assertThat(jpql).contains("u_2.id = :userId");
                        assertThat(jpql).contains("s_1.mapDifficulty.status = :rankedStatus");
                        assertThat(jpql).doesNotContain("s_1.user.id = :userId");

                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                        verify(mockQuery).setParameter("userId", 42L);
                }

                @Test
                void singleLevelSubquery_generatesDepthSuffixedAlias() {
                        MilestoneQuerySpec subquery = new MilestoneQuerySpec(
                                        new SelectSpec("PLAIN", "map_difficulty_uuid_id"),
                                        "map_difficulty_complexities",
                                        List.of(new FilterSpec("complexity", ">=", new BigDecimal("8.0"))));
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "scores",
                                        List.of(
                                                        new FilterSpec("active", "=", true),
                                                        new FilterSpec("map_difficulty_uuid_id", "IN", null,
                                                                        subquery)));

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(750));

                        service.evaluate(spec, 5L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        String jpql = jpqlCaptor.getValue();

                        assertThat(jpql).contains("s.mapDifficulty.id IN (");
                        assertThat(jpql).contains("FROM MapDifficultyComplexity mdc_1");
                        assertThat(jpql).contains("mdc_1.mapDifficulty.id");
                        assertThat(jpql).contains("mdc_1.mapDifficulty.status = :rankedStatus");
                        assertThat(jpql).doesNotContain(" mdc ");

                        verify(mockQuery).setParameter("p0", true);
                        verify(mockQuery).setParameter("p1", new BigDecimal("8.0"));
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
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

                        when(mockQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(980));

                        service.evaluate(spec, 42L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        String jpql = jpqlCaptor.getValue();

                        assertThat(jpql).contains("MAX(s.ap)");
                        assertThat(jpql).contains("s.misses = :p0");
                        assertThat(jpql).contains("s.badCuts = :p1");
                        assertThat(jpql).contains("s.mapDifficulty.id IN (");
                        assertThat(jpql).contains("FROM MapDifficultyComplexity mdc_1");
                        assertThat(jpql).contains("mdc_1.mapDifficulty.id");
                        assertThat(jpql).contains("mdc_1.complexity = (");
                        assertThat(jpql).contains("FROM MapDifficultyComplexity mdc_2");
                        assertThat(jpql).contains("MAX(mdc_2.complexity)");
                        assertThat(jpql).contains("mdc_1.mapDifficulty.status = :rankedStatus");
                        assertThat(jpql).contains("mdc_2.mapDifficulty.status = :rankedStatus");
                        assertThat(jpql).doesNotContain(" mdc ");

                        verify(mockQuery).setParameter("p0", 0);
                        verify(mockQuery).setParameter("p1", 0);
                        verify(mockQuery).setParameter("rankedStatus", MapDifficultyStatus.RANKED);
                }

                @Test
                void userCategoryStatistics_crossTable_categoryCode() {
                        MilestoneQuerySpec spec = new MilestoneQuerySpec(
                                        new SelectSpec("MAX", "ap"),
                                        "user_category_statistics",
                                        List.of(
                                                        new FilterSpec("active", "=", true),
                                                        new FilterSpec("category_code", "=", "true-acc")));

                        when(mockQuery.getSingleResult()).thenReturn(new BigDecimal("4200"));

                        service.evaluate(spec, 5L, null);

                        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
                        verify(entityManager).createQuery(jpqlCaptor.capture());
                        assertThat(jpqlCaptor.getValue()).contains("ucs.category.code");
                        verify(mockQuery).setParameter("p1", "true-acc");
                }
        }

        @Nested
        class GetSchema {

                @Test
                void returnsAllAllowedTables() {
                        MilestoneSchemaResponse schema = service.getSchema();

                        assertThat(schema.tables()).containsKeys(
                                        "scores", "user_category_statistics", "users",
                                        "user_milestone_links", "maps", "map_difficulties",
                                        "map_difficulty_statistics", "map_difficulty_complexities",
                                        "categories", "modifiers", "milestones", "milestone_sets",
                                        "level_thresholds");
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
                        assertThat(statusCol.enumValues()).containsExactlyInAnyOrder("QUEUE", "QUALIFIED", "RANKED");
                }

                @Test
                void difficultyColumn_includesAllDifficulties() {
                        MilestoneSchemaResponse schema = service.getSchema();

                        MilestoneSchemaResponse.ColumnInfo diffCol = schema.tables()
                                        .get("scores").stream()
                                        .filter(c -> c.name().equals("map_difficulty_difficulty"))
                                        .findFirst().orElseThrow();

                        assertThat(diffCol.enumValues())
                                        .containsExactlyInAnyOrder("EASY", "NORMAL", "HARD", "EXPERT", "EXPERT_PLUS");
                }

                @Test
                void functionsAndOperators_areIncluded() {
                        MilestoneSchemaResponse schema = service.getSchema();

                        assertThat(schema.functions()).containsExactlyInAnyOrder(
                                        "AVG", "COUNT", "COUNT_DISTINCT", "MAX", "MIN", "PLAIN", "SUM");
                        assertThat(schema.operators()).containsExactlyInAnyOrder(
                                        "!=", "<", "<=", "=", ">", ">=");
                }

                @Test
                void crossTableColumns_exposedInScores() {
                        MilestoneSchemaResponse schema = service.getSchema();

                        List<String> scoreColNames = schema.tables().get("scores").stream()
                                        .map(MilestoneSchemaResponse.ColumnInfo::name)
                                        .toList();

                        assertThat(scoreColNames).contains(
                                        "accuracy", "map_difficulty_status", "map_difficulty_difficulty",
                                        "song_name", "song_author", "map_author", "category_name", "category_code");
                }
        }
}
