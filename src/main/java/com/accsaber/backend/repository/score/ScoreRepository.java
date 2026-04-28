package com.accsaber.backend.repository.score;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.score.Score;

public interface ScoreRepository extends JpaRepository<Score, UUID> {

        Optional<Score> findByUser_IdAndMapDifficulty_IdAndActiveTrue(Long userId, UUID mapDifficultyId);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.user
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.map m
                        JOIN FETCH d.category
                        WHERE s.user.id = :userId AND s.active = true
                        AND m.songHash = :songHash
                        AND d.difficulty = :difficulty
                        AND d.characteristic = :characteristic
                        AND d.active = true
                        """)
        Optional<Score> findActiveByUserAndSongHashAndDifficultyAndCharacteristic(
                        @Param("userId") Long userId,
                        @Param("songHash") String songHash,
                        @Param("difficulty") Difficulty difficulty,
                        @Param("characteristic") String characteristic);

        List<Score> findByUser_IdAndMapDifficulty_IdInAndActiveTrue(Long userId, List<UUID> mapDifficultyIds);

        @Query("SELECT s FROM Score s JOIN FETCH s.user WHERE s.id = :id")
        Optional<Score> findByIdWithUser(@Param("id") UUID id);

        List<Score> findByUser_IdAndActiveTrue(Long userId);

        List<Score> findByMapDifficulty_IdAndActiveTrue(UUID mapDifficultyId);

        @Query("""
                        SELECT s FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        """)
        List<Score> findByMapDifficultyIdAndActiveTrueExcludingBanned(
                        @Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.category c
                        LEFT JOIN FETCH c.scoreCurve
                        WHERE d.id = :mapDifficultyId AND s.active = true
                        """)
        List<Score> findByMapDifficultyIdAndActiveTrueWithCategory(@Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.category c
                        LEFT JOIN FETCH c.scoreCurve
                        WHERE d.id = :mapDifficultyId AND s.active = true
                        """)
        List<Score> findByMapDifficultyIdAndActiveTrueWithUserAndCategory(
                        @Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("""
                        SELECT s FROM Score s
                        WHERE s.user.id = :userId
                        AND s.active = true
                        """)
        Page<Score> findActiveByUser(@Param("userId") Long userId, Pageable pageable);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        """)
        Page<Score> findByMapDifficulty_IdAndActiveTrue(@Param("mapDifficultyId") UUID mapDifficultyId,
                        Pageable pageable);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        """)
        Page<Score> findByMapDifficultyIdAndActiveTrueWithUser(
                        @Param("mapDifficultyId") UUID mapDifficultyId, Pageable pageable);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND u.country = :country
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND u.country = :country
                        """)
        Page<Score> findByMapDifficultyIdAndActiveTrueWithUserAndCountry(
                        @Param("mapDifficultyId") UUID mapDifficultyId,
                        @Param("country") String country,
                        Pageable pageable);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                        """)
        Page<Score> findByMapDifficultyIdAndActiveTrueWithUserAndSearch(
                        @Param("mapDifficultyId") UUID mapDifficultyId,
                        @Param("search") String search,
                        Pageable pageable);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND u.country = :country
                        AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND u.country = :country
                        AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                        """)
        Page<Score> findByMapDifficultyIdAndActiveTrueWithUserAndCountryAndSearch(
                        @Param("mapDifficultyId") UUID mapDifficultyId,
                        @Param("country") String country,
                        @Param("search") String search,
                        Pageable pageable);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.mapDifficulty d
                        WHERE s.user.id = :userId
                        AND d.category.id = :categoryId
                        AND s.active = true
                        ORDER BY s.ap DESC
                        """)
        List<Score> findActiveByUserAndCategoryOrderByApDesc(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT s FROM Score s
                        WHERE s.user.id = :userId
                        AND s.mapDifficulty.category.id = :categoryId
                        AND s.active = true
                        """)
        Page<Score> findActiveByUserAndCategory(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId,
                        Pageable pageable);

        @Query("""
                        SELECT s FROM Score s
                        WHERE s.user.id = :userId
                        AND s.active = true
                        AND LOWER(s.mapDifficulty.map.songName) LIKE LOWER(CONCAT('%', :search, '%'))
                        """)
        Page<Score> findActiveByUserAndSongNameSearch(
                        @Param("userId") Long userId,
                        @Param("search") String search,
                        Pageable pageable);

        @Query("""
                        SELECT s FROM Score s
                        WHERE s.user.id = :userId
                        AND s.mapDifficulty.category.id = :categoryId
                        AND s.active = true
                        AND LOWER(s.mapDifficulty.map.songName) LIKE LOWER(CONCAT('%', :search, '%'))
                        """)
        Page<Score> findActiveByUserAndCategoryAndSongNameSearch(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId,
                        @Param("search") String search,
                        Pageable pageable);

        @Query("SELECT COALESCE(SUM(s.score), 0) FROM Score s WHERE s.user.id = :userId AND s.active = true")
        long sumActiveScoreByUser(@Param("userId") Long userId);

        @Query("""
                        SELECT COALESCE(SUM(s.score), 0) FROM Score s
                        WHERE s.user.id = :userId AND s.active = true
                        AND s.mapDifficulty.category.id = :categoryId
                        """)
        long sumActiveScoreByUserAndCategory(@Param("userId") Long userId, @Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT COUNT(DISTINCT s.mapDifficulty.id) FROM Score s
                        WHERE s.user.id = :userId AND s.active = true
                        AND s.mapDifficulty.category.id = :categoryId
                        """)
        long countDistinctMapDifficultiesByUserAndCategory(
                        @Param("userId") Long userId, @Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT COUNT(DISTINCT s.mapDifficulty.id) FROM Score s
                        WHERE s.user.id = :userId AND s.active = true
                        """)
        long countDistinctMapDifficultiesByUser(@Param("userId") Long userId);

        @Query("""
                        SELECT DISTINCT s.user.id FROM Score s
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        """)
        List<Long> findDistinctUserIdsByMapDifficultyIdAndActiveTrue(
                        @Param("mapDifficultyId") UUID mapDifficultyId);

        List<Score> findByMapDifficulty_Id(UUID mapDifficultyId);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.category c
                        LEFT JOIN FETCH c.scoreCurve
                        WHERE d.id = :mapDifficultyId
                        ORDER BY s.user.id, s.timeSet ASC NULLS LAST
                        """)
        List<Score> findAllByDifficultyOrderedByUserAndTime(@Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("SELECT COALESCE(SUM(s.xpGained), 0) FROM Score s WHERE s.user.id = :userId")
        java.math.BigDecimal sumXpGainedByUserId(@Param("userId") Long userId);

        @Query("SELECT COALESCE(SUM(s.xpGained), 0) FROM Score s WHERE s.user.id = :userId AND s.createdAt >= :since")
        java.math.BigDecimal sumXpGainedByUserIdSince(@Param("userId") Long userId,
                        @Param("since") java.time.Instant since);

        @Query("SELECT DISTINCT s.mapDifficulty.id FROM Score s")
        List<UUID> findDistinctMapDifficultyIds();

        Optional<Score> findFirstByUser_IdAndActiveTrueOrderByTimeSetDesc(Long userId);

        @Query("SELECT DISTINCT s.user FROM Score s WHERE s.timeSet >= :since AND s.active = true")
        List<com.accsaber.backend.model.entity.user.User> findDistinctUsersWithScoresSince(
                        @Param("since") Instant since);

        @Modifying
        @Query(value = """
                        WITH ranked AS (
                                SELECT s.id, ROW_NUMBER() OVER (ORDER BY s.ap DESC, s.time_set ASC) AS new_rank
                                FROM scores s
                                JOIN users u ON s.user_id = u.id
                                WHERE s.map_difficulty_id = :difficultyId AND s.active = true
                                AND u.active = true AND u.banned = false
                        )
                        UPDATE scores s SET rank = r.new_rank, updated_at = NOW()
                        FROM ranked r WHERE s.id = r.id
                        """, nativeQuery = true)
        void reassignScoreRanks(@Param("difficultyId") UUID difficultyId);

        @Modifying
        @Query(value = """
                        UPDATE scores SET rank_when_set = rank, updated_at = NOW()
                        WHERE map_difficulty_id = :difficultyId AND active = true
                        """, nativeQuery = true)
        void syncRankWhenSetFromRank(@Param("difficultyId") UUID difficultyId);

        @Query(value = """
                        SELECT DISTINCT map_difficulty_id FROM scores WHERE active = true
                        """, nativeQuery = true)
        List<UUID> findDistinctActiveDifficultyIds();

        @Query(value = """
                        SELECT * FROM scores
                        WHERE user_id = :userId AND map_difficulty_id = :difficultyId
                        AND time_set >= CAST(:since AS timestamptz)
                        ORDER BY time_set ASC
                        """, nativeQuery = true)
        List<Score> findHistoric(
                        @Param("userId") Long userId,
                        @Param("difficultyId") UUID difficultyId,
                        @Param("since") Instant since);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        WHERE s.mapDifficulty.id = :difficultyId
                        AND s.rankWhenSet = 1
                        AND u.active = true AND u.banned = false
                        ORDER BY s.createdAt ASC
                        """)
        List<Score> findAllTopOnes(@Param("difficultyId") UUID difficultyId);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        WHERE s.mapDifficulty.id = :difficultyId
                        AND s.rank = 1
                        AND s.active = true
                        AND u.active = true AND u.banned = false
                        """)
        Optional<Score> findCurrentTopOne(@Param("difficultyId") UUID difficultyId);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        WHERE s.mapDifficulty.id IN :difficultyIds
                        AND s.rank = 1
                        AND s.active = true
                        AND u.active = true AND u.banned = false
                        """)
        List<Score> findCurrentTopOnes(@Param("difficultyIds") List<UUID> difficultyIds);

        @Query(value = """
                        SELECT COUNT(*) FROM scores s
                        JOIN users u ON s.user_id = u.id
                        WHERE s.map_difficulty_id = :difficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND (s.ap > :ap OR (s.ap = :ap AND s.time_set < :timeSet))
                        """, nativeQuery = true)
        int countActiveScoresRankedAbove(@Param("difficultyId") UUID difficultyId,
                        @Param("ap") java.math.BigDecimal ap,
                        @Param("timeSet") Instant timeSet);

        @Modifying
        @Query(value = """
                        UPDATE scores SET rank = rank + 1, updated_at = NOW()
                        WHERE map_difficulty_id = :difficultyId AND active = true AND rank >= :fromRank
                        """, nativeQuery = true)
        void shiftScoreRanksDown(@Param("difficultyId") UUID difficultyId, @Param("fromRank") int fromRank);

        @Modifying
        @Query(value = """
                        UPDATE scores SET rank = rank - 1, updated_at = NOW()
                        WHERE map_difficulty_id = :difficultyId AND active = true AND rank > :removedRank
                        """, nativeQuery = true)
        void shiftScoreRanksUp(@Param("difficultyId") UUID difficultyId, @Param("removedRank") int removedRank);

        @Query("SELECT s.id FROM Score s WHERE s.user.id = :userId AND s.mapDifficulty.id = :difficultyId")
        List<UUID> findIdsByUserAndDifficulty(@Param("userId") Long userId,
                        @Param("difficultyId") UUID difficultyId);

        @Modifying
        @Query(value = "UPDATE scores SET supersedes_id = NULL WHERE supersedes_id IN (:scoreIds)", nativeQuery = true)
        void nullifySupersedesReferences(@Param("scoreIds") List<UUID> scoreIds);

        @Modifying
        @Query(value = "UPDATE user_category_statistics SET top_play_id = NULL WHERE top_play_id IN (:scoreIds)", nativeQuery = true)
        void nullifyTopPlayReferences(@Param("scoreIds") List<UUID> scoreIds);

        @Modifying
        @Query(value = "UPDATE user_milestone_links SET achieved_with_score_id = NULL WHERE achieved_with_score_id IN (:scoreIds)", nativeQuery = true)
        void nullifyMilestoneScoreReferences(@Param("scoreIds") List<UUID> scoreIds);


        @Modifying
        @Query(value = "DELETE FROM merge_score_actions WHERE score_id IN (:scoreIds)", nativeQuery = true)
        void deleteMergeScoreActions(@Param("scoreIds") List<UUID> scoreIds);

        @Modifying
        @Query(value = "DELETE FROM scores WHERE id IN (:scoreIds)", nativeQuery = true)
        void hardDeleteByIds(@Param("scoreIds") List<UUID> scoreIds);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.map m
                        JOIN FETCH d.category c
                        WHERE s.streak115 IS NOT NULL
                        AND (s.active = true OR s.supersedesReason = 'Score improved')
                        AND u.active = true AND u.banned = false
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        WHERE s.streak115 IS NOT NULL
                        AND (s.active = true OR s.supersedesReason = 'Score improved')
                        AND u.active = true AND u.banned = false
                        """)
        Page<Score> findTopStreaks(Pageable pageable);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.map m
                        JOIN FETCH d.category c
                        WHERE s.streak115 IS NOT NULL
                        AND (s.active = true OR s.supersedesReason = 'Score improved')
                        AND d.category.id = :categoryId
                        AND u.active = true AND u.banned = false
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        JOIN s.mapDifficulty d
                        WHERE s.streak115 IS NOT NULL
                        AND (s.active = true OR s.supersedesReason = 'Score improved')
                        AND d.category.id = :categoryId
                        AND u.active = true AND u.banned = false
                        """)
        Page<Score> findTopStreaksByCategory(@Param("categoryId") UUID categoryId, Pageable pageable);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.map m
                        JOIN FETCH d.category c
                        WHERE s.active = true
                        AND u.active = true AND u.banned = false
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        WHERE s.active = true
                        AND u.active = true AND u.banned = false
                        """)
        Page<Score> findTopByAp(Pageable pageable);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.map m
                        JOIN FETCH d.category c
                        WHERE s.active = true
                        AND d.category.id = :categoryId
                        AND u.active = true AND u.banned = false
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        JOIN s.mapDifficulty d
                        WHERE s.active = true
                        AND d.category.id = :categoryId
                        AND u.active = true AND u.banned = false
                        """)
        Page<Score> findTopByApAndCategory(@Param("categoryId") UUID categoryId, Pageable pageable);

        @Query(value = """
                        SELECT s_b, s_a
                        FROM Score s_b
                        JOIN FETCH s_b.mapDifficulty d
                        JOIN FETCH d.map
                        JOIN FETCH d.category c
                        JOIN Score s_a
                          ON s_a.mapDifficulty = s_b.mapDifficulty
                         AND s_a.user.id = :sniperId
                         AND s_a.active = true
                        WHERE s_b.user.id = :targetId
                          AND s_b.active = true
                          AND d.active = true
                          AND s_b.score > s_a.score
                          AND (:categoryId IS NULL OR c.id = :categoryId)
                          AND (:overallOnly = false OR c.countForOverall = true)
                        ORDER BY (s_b.ap - s_a.ap) ASC
                        """, countQuery = """
                        SELECT COUNT(s_b)
                        FROM Score s_b
                        JOIN s_b.mapDifficulty d
                        JOIN d.category c
                        JOIN Score s_a
                          ON s_a.mapDifficulty = s_b.mapDifficulty
                         AND s_a.user.id = :sniperId
                         AND s_a.active = true
                        WHERE s_b.user.id = :targetId
                          AND s_b.active = true
                          AND d.active = true
                          AND s_b.score > s_a.score
                          AND (:categoryId IS NULL OR c.id = :categoryId)
                          AND (:overallOnly = false OR c.countForOverall = true)
                        """)
        Page<Object[]> findClosestSnipePairs(
                        @Param("sniperId") Long sniperId,
                        @Param("targetId") Long targetId,
                        @Param("categoryId") UUID categoryId,
                        @Param("overallOnly") boolean overallOnly,
                        Pageable pageable);
}
