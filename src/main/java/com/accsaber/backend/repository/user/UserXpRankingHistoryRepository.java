package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.user.UserXpRankingHistory;

public interface UserXpRankingHistoryRepository extends JpaRepository<UserXpRankingHistory, UUID> {

        @Modifying
        @Query(value = """
                        INSERT INTO user_xp_ranking_history (id, user_id, xp_ranking, xp_country_ranking, recorded_at)
                        SELECT gen_random_uuid(), u.id, u.xp_ranking, u.xp_country_ranking, NOW()
                        FROM users u
                        LEFT JOIN LATERAL (
                                SELECT h.xp_ranking, h.xp_country_ranking
                                FROM user_xp_ranking_history h
                                WHERE h.user_id = u.id
                                ORDER BY h.recorded_at DESC
                                LIMIT 1
                        ) prev ON true
                        WHERE u.active = true AND u.banned = false AND u.xp_ranking IS NOT NULL
                        AND (prev.xp_ranking IS NULL
                                OR prev.xp_ranking IS DISTINCT FROM u.xp_ranking
                                OR prev.xp_country_ranking IS DISTINCT FROM u.xp_country_ranking)
                        """, nativeQuery = true)
        void snapshotChangedRankings();

        @Query(value = """
                        SELECT DISTINCT ON (h.user_id) h.user_id, h.xp_ranking
                        FROM user_xp_ranking_history h
                        WHERE h.user_id IN :userIds
                        AND h.recorded_at <= NOW() - INTERVAL '7 days'
                        ORDER BY h.user_id, h.recorded_at DESC
                        """, nativeQuery = true)
        List<Object[]> findRankingsOneWeekAgo(@Param("userIds") List<Long> userIds);
}
