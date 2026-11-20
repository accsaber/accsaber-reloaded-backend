package com.accsaber.backend.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.dto.response.statistics.TimeSeriesPointResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.score.ScoreRepository;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(StatsQueryRunner.class)
class SiteStatisticsQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final Long FOLLOWED = 76561190000000901L;
    private static final Long STRANGER = 76561190000000902L;
    private static final SiteStatsFilter RELATION = new SiteStatsFilter("ES", List.of(FOLLOWED));

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private StatsQueryRunner queryRunner;
    @Autowired
    private ScoreRepository scoreRepository;

    @BeforeEach
    void seed() {
        entityManager.persist(User.builder().id(FOLLOWED).name("Followed").country("ES").build());
        entityManager.persist(User.builder().id(STRANGER).name("Stranger").country("ES").build());
        entityManager.flush();
    }

    static Stream<BiFunction<SiteStatsFilter, Map<String, Object>, String>> queries() {
        return Stream.of(
                (f, p) -> SiteStatisticsQueries.highestAvgAp(null, f, 1, p),
                (f, p) -> SiteStatisticsQueries.mostRetriedMaps(null, f, p),
                (f, p) -> SiteStatisticsQueries.mostImprovements(null, f, p),
                (f, p) -> SiteStatisticsQueries.mostMapImprovements(null, f, p),
                SiteStatisticsQueries::milestoneCollectors,
                (f, p) -> SiteStatisticsQueries.newPlayersPerDay("day", f, since(p)),
                (f, p) -> SiteStatisticsQueries.scoresPerDay("day", f, since(p)),
                (f, p) -> SiteStatisticsQueries.cumulativeAccounts("day", f, since(p)),
                (f, p) -> SiteStatisticsQueries.cumulativeScores("day", f, since(p)),
                SiteStatisticsQueries::scoresPerCategory,
                SiteStatisticsQueries::playersByHmd,
                (f, p) -> SiteStatisticsQueries.mostItems(null, null, f, p),
                (f, p) -> SiteStatisticsQueries.mostCratesOpened(null, f, p),
                SiteStatisticsQueries::rarestUnboxed,
                SiteStatisticsQueries::mostValuableInventory,
                SiteStatisticsQueries::firstEditions,
                SiteStatisticsQueries::firstEditionHolders,
                SiteStatisticsQueries::mostCompleteCollection,
                SiteStatisticsQueries::biggestTraders,
                SiteStatisticsQueries::mostEssenceEarned);
    }

    @ParameterizedTest
    @MethodSource("queries")
    @DisplayName("every stat runs with a country and a relation filter")
    void everyStatRunsWithARelationFilter(BiFunction<SiteStatsFilter, Map<String, Object>, String> query) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = query.apply(RELATION, params);

        assertThatCode(() -> queryRunner.list(sql, params, row -> row)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a relation filter keeps only those players, and an empty one keeps nobody")
    void relationFilterNarrowsThePlayers() {
        assertThat(newPlayers(new SiteStatsFilter(null, null))).isEqualTo(2);
        assertThat(newPlayers(RELATION)).isEqualTo(1);
        assertThat(newPlayers(new SiteStatsFilter(null, List.of()))).isZero();
    }

    @Test
    @DisplayName("the top score queries take a relation filter")
    void topScoreQueriesTakeARelationFilter() {
        PageRequest page = PageRequest.of(0, 5);

        assertThat(scoreRepository.findTopApIds(null, null, null, page)).isEmpty();
        assertThat(scoreRepository.findTopApIds(null, "ES", List.of(FOLLOWED, STRANGER), page)).isEmpty();
        assertThat(scoreRepository.findTopStreakIds(null, null, null, page)).isEmpty();
        assertThat(scoreRepository.findTopStreakIds(null, "ES", List.of(FOLLOWED, STRANGER), page)).isEmpty();
    }

    private long newPlayers(SiteStatsFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.newPlayersPerDay("day", filter, since(params));
        return queryRunner.timeSeries(sql, params).stream().mapToLong(TimeSeriesPointResponse::getValue).sum();
    }

    private static Map<String, Object> since(Map<String, Object> params) {
        params.put("since", Instant.EPOCH);
        return params;
    }
}
