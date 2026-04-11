package com.accsaber.backend.repository.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.user.UserCategoryRankingHistory;

public interface UserCategoryRankingHistoryRepository extends JpaRepository<UserCategoryRankingHistory, UUID> {

        @Modifying
        @Query(value = """
                        INSERT INTO user_category_ranking_history (id, user_id, category_id, ranking, country_ranking, recorded_at)
                        SELECT uuidv7(), ucs.user_id, ucs.category_id, ucs.ranking, ucs.country_ranking, NOW()
                        FROM user_category_statistics ucs
                        JOIN users u ON u.id = ucs.user_id
                        LEFT JOIN LATERAL (
                                SELECT h.ranking, h.country_ranking
                                FROM user_category_ranking_history h
                                WHERE h.user_id = ucs.user_id AND h.category_id = ucs.category_id
                                ORDER BY h.recorded_at DESC
                                LIMIT 1
                        ) prev ON true
                        WHERE ucs.active = true AND u.active = true AND u.banned = false AND ucs.ranking IS NOT NULL
                        AND (prev.ranking IS NULL
                                OR prev.ranking IS DISTINCT FROM ucs.ranking
                                OR prev.country_ranking IS DISTINCT FROM ucs.country_ranking)
                        """, nativeQuery = true)
        void snapshotChangedRankings();

        @Query(value = """
                        SELECT h.* FROM user_category_ranking_history h
                        JOIN categories c ON c.id = h.category_id
                        WHERE h.user_id = :userId AND c.code = :categoryCode
                        AND h.recorded_at >= CAST(:since AS timestamptz)
                        ORDER BY h.recorded_at ASC
                        """, nativeQuery = true)
        List<UserCategoryRankingHistory> findByUserAndCategoryCodeSince(
                        @Param("userId") Long userId,
                        @Param("categoryCode") String categoryCode,
                        @Param("since") Instant since);
}
