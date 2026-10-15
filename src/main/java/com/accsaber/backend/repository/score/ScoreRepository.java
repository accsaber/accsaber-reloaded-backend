package com.accsaber.backend.repository.score;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.dto.projection.SimulationScoreRow;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;

public interface ScoreRepository extends JpaRepository<Score, UUID> {

        @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))", nativeQuery = true)
        Object acquireSubmitLock(@Param("key") String key);

        Optional<Score> findByUser_IdAndMapDifficulty_IdAndActiveTrue(Long userId, UUID mapDifficultyId);

        @Query("""
                        SELECT s FROM Score s
                        WHERE s.user.id = :userId
                          AND s.mapDifficulty.id = :mapDifficultyId
                          AND s.partial = :partial
                          AND ((:campaignAttempt = true AND s.supersedesReason = 'Campaign attempt')
                               OR (:campaignAttempt = false
                                   AND (s.supersedesReason IS NULL OR s.supersedesReason <> 'Campaign attempt')))
                          AND (s.createdAt >= :since OR (s.timeSet IS NOT NULL AND s.timeSet >= :since))
                          AND (s.scoreNoMods = :scoreNoMods
                               OR (s.timeSet IS NOT NULL AND s.timeSet BETWEEN :playFrom AND :playTo))
                        ORDER BY CASE WHEN s.scoreNoMods = :scoreNoMods THEN 0 ELSE 1 END,
                                 COALESCE(s.timeSet, s.createdAt) DESC
                        """)
        List<Score> findRecentMatchingPlay(
                        @Param("userId") Long userId,
                        @Param("mapDifficultyId") UUID mapDifficultyId,
                        @Param("scoreNoMods") Integer scoreNoMods,
                        @Param("partial") boolean partial,
                        @Param("campaignAttempt") boolean campaignAttempt,
                        @Param("since") Instant since,
                        @Param("playFrom") Instant playFrom,
                        @Param("playTo") Instant playTo,
                        Pageable pageable);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.mapDifficulty
                        WHERE s.user.id = :userId
                          AND s.mapDifficulty.id IN :mapDifficultyIds
                          AND s.partial = false
                          AND (s.mapDifficulty.maxScore IS NULL OR s.scoreNoMods <= s.mapDifficulty.maxScore)
                          AND COALESCE(s.timeSet, s.createdAt) >= :since
                        """)
        List<Score> findEligibleCampaignRows(
                        @Param("userId") Long userId,
                        @Param("mapDifficultyIds") Collection<UUID> mapDifficultyIds,
                        @Param("since") Instant since);

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

        @Query("SELECT s FROM Score s JOIN FETCH s.user WHERE s.id = :id")
        Optional<Score> findByIdWithUser(@Param("id") UUID id);

        List<Score> findByUser_IdAndActiveTrue(Long userId);

        @Query("""
                        SELECT s FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        """)
        List<Score> findByMapDifficultyIdAndActiveTrueExcludingBanned(
                        @Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("""
                        SELECT MAX(s.ap) FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.category.id = :categoryId
                        AND s.active = true
                        AND u.active = true AND u.banned = false
                        """)
        Double findMaxApInCategory(@Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT MAX(s.weightedAp) FROM Score s
                        WHERE s.user.id = :userId
                          AND s.mapDifficulty.category.id = :categoryId
                          AND s.active = true
                        """)
        Double findMaxWeightedApByUserAndCategory(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.category c
                        LEFT JOIN FETCH c.scoreCurve
                        WHERE d.id = :mapDifficultyId AND s.active = true
                        """)
        List<Score> findByMapDifficultyIdAndActiveTrueWithCategory(@Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("""
                        SELECT s.id FROM Score s
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        """)
        List<UUID> findActiveIdsByMapDifficultyId(@Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("""
                        SELECT s.mapDifficulty.id, s.mapDifficulty.map.songHash,
                               s.mapDifficulty.ssLeaderboardId, s.mapDifficulty.blLeaderboardId,
                               s.ap, s.score, s.mapDifficulty.maxScore, s.rank,
                               s.blScoreId, s.ssScoreId, s.timeSet,
                               s.mapDifficulty.map.songName, s.mapDifficulty.map.songSubName,
                               s.mapDifficulty.map.songAuthor,
                               s.mapDifficulty.map.coverUrl, s.mapDifficulty.map.cdnCoverUrl
                        FROM Score s
                        WHERE s.user.id = :userId AND s.active = true
                        ORDER BY s.ap DESC
                        """)
        List<Object[]> findActiveScoreSummariesByUser(@Param("userId") Long userId);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND (CAST(:country AS string) IS NULL OR u.country = CAST(:country AS string))
                        AND (CAST(:search AS string) IS NULL OR u.id IN (SELECT sn.userId FROM UserSearchName sn WHERE sn.searchName LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')))
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND (CAST(:country AS string) IS NULL OR u.country = CAST(:country AS string))
                        AND (CAST(:search AS string) IS NULL OR u.id IN (SELECT sn.userId FROM UserSearchName sn WHERE sn.searchName LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')))
                        """)
        Page<Score> findLeaderboardByMapDifficulty(
                        @Param("mapDifficultyId") UUID mapDifficultyId,
                        @Param("country") String country,
                        @Param("search") String search,
                        Pageable pageable);

        @Query(value = """
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND u.id IN :userIds
                        AND (CAST(:country AS string) IS NULL OR u.country = CAST(:country AS string))
                        AND (CAST(:search AS string) IS NULL OR u.id IN (SELECT sn.userId FROM UserSearchName sn WHERE sn.searchName LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')))
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        AND u.active = true AND u.banned = false
                        AND u.id IN :userIds
                        AND (CAST(:country AS string) IS NULL OR u.country = CAST(:country AS string))
                        AND (CAST(:search AS string) IS NULL OR u.id IN (SELECT sn.userId FROM UserSearchName sn WHERE sn.searchName LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')))
                        """)
        Page<Score> findByMapDifficultyIdAndActiveTrueWithUserFilteredByUserIds(
                        @Param("mapDifficultyId") UUID mapDifficultyId,
                        @Param("userIds") Collection<Long> userIds,
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
                        SELECT s.ap FROM Score s
                        JOIN s.mapDifficulty d
                        WHERE s.user.id = :userId
                        AND d.category.id = :categoryId
                        AND s.active = true
                        ORDER BY s.ap DESC
                        """)
        List<Double> findActiveApsByUserAndCategoryOrderByApDesc(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT MAX(s.ap) FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId
                          AND s.active = true
                          AND u.active = true AND u.banned = false
                        """)
        Double findMaxApByMapDifficulty(@Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.map
                        JOIN FETCH d.category
                        WHERE s.user.id = :userId
                          AND d.category.id = :categoryId
                          AND s.active = true
                          AND s.timeSet IS NOT NULL
                          AND s.timeSet < :before
                        ORDER BY s.timeSet ASC
                        """)
        List<Score> findActiveByUserAndCategoryOlderThan(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId,
                        @Param("before") Instant before);

        @Query("""
                        SELECT new com.accsaber.backend.model.dto.projection.SimulationScoreRow(
                            u.id, d.id, d.category.id, s.score, d.maxScore, s.ap)
                        FROM Score s
                        JOIN s.user u
                        JOIN s.mapDifficulty d
                        WHERE s.active = true
                          AND d.active = true
                          AND d.status = :status
                          AND u.active = true AND u.banned = false
                        """)
        List<SimulationScoreRow> findActiveRowsByDifficultyStatus(@Param("status") MapDifficultyStatus status);

        @Query("""
                        SELECT s.streak115 FROM Score s
                        LEFT JOIN com.accsaber.backend.model.entity.map.MapDifficultyComplexity c
                          ON c.mapDifficulty = s.mapDifficulty AND c.active = true
                        WHERE s.user.id = :userId
                          AND s.mapDifficulty.category.id = :categoryId
                          AND s.active = true
                          AND s.streak115 IS NOT NULL
                          AND (:minComplexity IS NULL OR c.complexity >= :minComplexity)
                          AND (:maxComplexityExclusive IS NULL OR c.complexity < :maxComplexityExclusive)
                        ORDER BY s.streak115 DESC
                        """)
        List<Integer> findTopStreak115ValuesByUserAndCategory(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId,
                        @Param("minComplexity") Double minComplexity,
                        @Param("maxComplexityExclusive") Double maxComplexityExclusive,
                        Pageable pageable);

        @Query("""
                        SELECT s.ap / (c.complexity - :shift) FROM Score s
                        JOIN com.accsaber.backend.model.entity.map.MapDifficultyComplexity c
                          ON c.mapDifficulty = s.mapDifficulty AND c.active = true
                        WHERE s.user.id = :userId
                          AND s.mapDifficulty.category.id = :categoryId
                          AND s.active = true
                          AND c.complexity > :shift
                          AND c.complexity >= :minComplexity
                          AND c.complexity < :maxComplexityExclusive
                        ORDER BY s.ap / (c.complexity - :shift) DESC
                        """)
        List<Double> findTopApPerComplexityByUserAndCategoryAndComplexityRange(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId,
                        @Param("minComplexity") Double minComplexity,
                        @Param("maxComplexityExclusive") Double maxComplexityExclusive,
                        @Param("shift") Double shift,
                        Pageable pageable);

        @Query("""
                        SELECT s, ucs.skillLevel FROM Score s
                        JOIN FETCH s.user u
                        JOIN com.accsaber.backend.model.entity.user.UserCategorySkill ucs
                          ON ucs.user.id = u.id AND ucs.category.id = :categoryId
                        WHERE s.mapDifficulty.id = :mapDifficultyId
                          AND s.active = true
                          AND u.active = true AND u.banned = false
                          AND s.user.id <> :excludeUserId
                          AND s.score > :minScore
                          AND s.ap IS NOT NULL
                          AND ucs.skillLevel IS NOT NULL
                        ORDER BY ABS(s.ap - :targetAp) ASC
                        """)
        List<Object[]> findSnipeCandidatesAboveBaselineWithSkill(
                        @Param("mapDifficultyId") UUID mapDifficultyId,
                        @Param("excludeUserId") Long excludeUserId,
                        @Param("minScore") Integer minScore,
                        @Param("categoryId") UUID categoryId,
                        @Param("targetAp") Double targetAp,
                        Pageable pageable);

        @Query("""
                        SELECT s.ap, ucs.skillLevel FROM Score s
                        JOIN s.user u
                        JOIN com.accsaber.backend.model.entity.user.UserCategorySkill ucs
                          ON ucs.user.id = u.id AND ucs.category.id = :categoryId
                        WHERE s.mapDifficulty.id = :mapDifficultyId
                          AND s.active = true
                          AND u.active = true AND u.banned = false
                          AND s.ap IS NOT NULL
                          AND ucs.skillLevel IS NOT NULL
                        ORDER BY s.ap DESC
                        """)
        List<Object[]> findLeaderboardApAndSkill(
                        @Param("mapDifficultyId") UUID mapDifficultyId,
                        @Param("categoryId") UUID categoryId);


        @Query("""
                        SELECT s.streak115 FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId
                          AND s.active = true
                          AND s.streak115 IS NOT NULL
                          AND u.active = true AND u.banned = false
                        ORDER BY s.streak115 DESC
                        """)
        List<Integer> findTopStreak115ValuesByMapDifficulty(
                        @Param("mapDifficultyId") UUID mapDifficultyId,
                        Pageable pageable);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.user
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.map
                        WHERE s.user.id = :userId AND s.active = true
                        ORDER BY s.ap DESC
                        """)
        List<Score> findActiveByUserOrderByApDescWithMap(@Param("userId") Long userId);

        @Query("""
                        SELECT s.user.id FROM Score s
                        JOIN s.user u
                        WHERE s.active = true AND u.active = true AND u.banned = false
                        GROUP BY s.user.id
                        HAVING COUNT(s) >= :minScores
                        """)
        List<Long> findUserIdsWithAtLeastActiveScores(@Param("minScores") long minScores);

        @Query("""
                        SELECT s.user.id FROM Score s
                        JOIN s.user u
                        WHERE s.active = true AND u.active = true AND u.banned = false
                          AND u.playerInactive = false
                        GROUP BY s.user.id
                        HAVING COUNT(s) >= :minScores
                           AND MAX(COALESCE(s.timeSet, s.createdAt)) >= :playedSince
                        """)
        List<Long> findRecentlyActivePlayerIds(@Param("minScores") long minScores,
                        @Param("playedSince") Instant playedSince);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        WHERE s.user.id IN :userIds
                        AND s.active = true
                        AND (:categoryId IS NULL OR s.mapDifficulty.category.id = :categoryId)
                        AND (CAST(:search AS string) IS NULL
                        OR search_normalize(s.mapDifficulty.map.songName) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
                        OR search_normalize(s.mapDifficulty.map.songSubName) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%'))
                        """)
        Page<Score> findActiveByUsers(
                        @Param("userIds") Collection<Long> userIds,
                        @Param("categoryId") UUID categoryId,
                        @Param("search") String search,
                        Pageable pageable);

        @Query(value = """
                        SELECT c.id, c.code, ranked.ap
                        FROM (SELECT md.category_id, s.ap,
                                     ROW_NUMBER() OVER (PARTITION BY md.category_id ORDER BY s.ap DESC) AS position
                              FROM scores s
                              JOIN map_difficulties md ON md.id = s.map_difficulty_id
                              WHERE s.user_id = :userId AND s.active = true) ranked
                        JOIN categories c ON c.id = ranked.category_id
                        WHERE ranked.position <= :limit AND c.active = true
                        ORDER BY c.code, ranked.position
                        """, nativeQuery = true)
        List<Object[]> findTopApByCategory(@Param("userId") Long userId, @Param("limit") int limit);

        @Query("""
                        SELECT s FROM Score s
                        WHERE s.user.id = :userId
                        AND s.mapDifficulty.id IN :mapDifficultyIds
                        AND s.active = true
                        """)
        List<Score> findActiveByUserAndMapDifficultyIdIn(
                        @Param("userId") Long userId,
                        @Param("mapDifficultyIds") Collection<UUID> mapDifficultyIds);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.category c
                        LEFT JOIN FETCH c.scoreCurve
                        WHERE d.id = :mapDifficultyId
                          AND (s.supersedesReason IS NULL OR s.supersedesReason <> 'Campaign attempt')
                        ORDER BY s.user.id, COALESCE(s.timeSet, s.createdAt) ASC, s.createdAt ASC, s.id ASC
                        """)
        List<Score> findAllByDifficultyOrderedByUserAndTime(@Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("""
                        SELECT s.user.id, s.mapDifficulty.id,
                               MAX(CASE WHEN s.supersedesReason IS NULL OR s.supersedesReason <> 'Campaign attempt'
                                        THEN s.streak115 END),
                               MAX(s.playCount),
                               MAX(COALESCE(s.timeSet, s.createdAt))
                        FROM Score s
                        WHERE s.user.id IN :userIds
                          AND s.mapDifficulty.id IN :mapDifficultyIds
                        GROUP BY s.user.id, s.mapDifficulty.id
                        """)
        List<Object[]> findAttemptAggregatesByUsersAndDifficulties(
                        @Param("userIds") Collection<Long> userIds,
                        @Param("mapDifficultyIds") Collection<UUID> mapDifficultyIds);

        @Query("SELECT COALESCE(SUM(s.xpGained), 0) FROM Score s WHERE s.user.id = :userId")
        double sumXpGainedByUserId(@Param("userId") Long userId);

        @Query("""
                        SELECT COUNT(s) FROM Score s
                        WHERE s.user.id = :userId
                          AND s.mapDifficulty.id = :mapDifficultyId
                          AND s.reweightDerivative = false
                        """)
        long countAttemptsByUserAndDifficulty(
                        @Param("userId") Long userId,
                        @Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("SELECT COALESCE(SUM(s.xpGained), 0) FROM Score s WHERE s.user.id = :userId AND s.createdAt >= :since")
        double sumXpGainedByUserIdSince(@Param("userId") Long userId,
                        @Param("since") Instant since);

        @Query("SELECT DISTINCT s.mapDifficulty.id FROM Score s")
        List<UUID> findDistinctMapDifficultyIds();

        @Query("""
                        SELECT DISTINCT s.mapDifficulty.id FROM Score s
                        WHERE s.user.id = :userId AND s.active = true
                        """)
        List<UUID> findDistinctMapDifficultyIdsByUser(@Param("userId") Long userId);

        Optional<Score> findFirstByUser_IdAndActiveTrueOrderByTimeSetDesc(Long userId);

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
                        SELECT * FROM (
                            SELECT * FROM scores
                            WHERE user_id = :userId AND map_difficulty_id = :difficultyId
                            AND (supersedes_reason IS NULL OR supersedes_reason <> 'Campaign attempt')
                            AND time_set >= CAST(:since AS timestamptz)
                            UNION ALL
                            (SELECT * FROM scores
                            WHERE user_id = :userId AND map_difficulty_id = :difficultyId
                            AND (supersedes_reason IS NULL OR supersedes_reason <> 'Campaign attempt')
                            AND time_set < CAST(:since AS timestamptz)
                            ORDER BY time_set DESC
                            LIMIT 1)
                        ) combined
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
                        @Param("ap") Double ap,
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
                        SELECT s.id FROM Score s
                        JOIN s.user u
                        JOIN s.mapDifficulty d
                        WHERE s.streak115 IS NOT NULL
                        AND (s.supersedesReason IS NULL OR s.supersedesReason <> 'Campaign attempt')
                        AND u.active = true AND u.banned = false
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (CAST(:country AS string) IS NULL OR LOWER(u.country) = LOWER(CAST(:country AS string)))
                        AND NOT EXISTS (
                            SELECT 1 FROM Score s2
                            WHERE s2.user.id = s.user.id
                            AND s2.mapDifficulty.id = s.mapDifficulty.id
                            AND s2.streak115 IS NOT NULL
                            AND (s2.supersedesReason IS NULL OR s2.supersedesReason <> 'Campaign attempt')
                            AND (s2.streak115 > s.streak115
                                OR (s2.streak115 = s.streak115 AND s2.ap > s.ap)
                                OR (s2.streak115 = s.streak115 AND s2.ap = s.ap AND s2.score > s.score)
                                OR (s2.streak115 = s.streak115 AND s2.ap = s.ap AND s2.score = s.score AND s2.id > s.id))
                        )
                        ORDER BY s.streak115 DESC, s.ap DESC, s.score DESC
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        JOIN s.mapDifficulty d
                        WHERE s.streak115 IS NOT NULL
                        AND (s.supersedesReason IS NULL OR s.supersedesReason <> 'Campaign attempt')
                        AND u.active = true AND u.banned = false
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (CAST(:country AS string) IS NULL OR LOWER(u.country) = LOWER(CAST(:country AS string)))
                        AND NOT EXISTS (
                            SELECT 1 FROM Score s2
                            WHERE s2.user.id = s.user.id
                            AND s2.mapDifficulty.id = s.mapDifficulty.id
                            AND s2.streak115 IS NOT NULL
                            AND (s2.supersedesReason IS NULL OR s2.supersedesReason <> 'Campaign attempt')
                            AND (s2.streak115 > s.streak115
                                OR (s2.streak115 = s.streak115 AND s2.ap > s.ap)
                                OR (s2.streak115 = s.streak115 AND s2.ap = s.ap AND s2.score > s.score)
                                OR (s2.streak115 = s.streak115 AND s2.ap = s.ap AND s2.score = s.score AND s2.id > s.id))
                        )
                        """)
        Page<UUID> findTopStreakIds(@Param("categoryId") UUID categoryId, @Param("country") String country,
                        Pageable pageable);

        @Query(value = """
                        SELECT s.id FROM Score s
                        JOIN s.user u
                        JOIN s.mapDifficulty d
                        WHERE s.active = true
                        AND u.active = true AND u.banned = false
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (CAST(:country AS string) IS NULL OR LOWER(u.country) = LOWER(CAST(:country AS string)))
                        ORDER BY s.ap DESC, s.score DESC
                        """, countQuery = """
                        SELECT COUNT(s) FROM Score s
                        JOIN s.user u
                        JOIN s.mapDifficulty d
                        WHERE s.active = true
                        AND u.active = true AND u.banned = false
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (CAST(:country AS string) IS NULL OR LOWER(u.country) = LOWER(CAST(:country AS string)))
                        """)
        Page<UUID> findTopApIds(@Param("categoryId") UUID categoryId, @Param("country") String country,
                        Pageable pageable);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.user u
                        JOIN FETCH s.mapDifficulty d
                        JOIN FETCH d.map m
                        JOIN FETCH d.category c
                        WHERE s.id IN :ids
                        """)
        List<Score> findDetailedByIds(@Param("ids") List<UUID> ids);

        @Query(value = """
                        SELECT s_b, s_a
                        FROM Score s_b
                        JOIN FETCH s_b.mapDifficulty d
                        JOIN FETCH d.map
                        JOIN FETCH d.category c
                        LEFT JOIN Score s_a
                          ON s_a.mapDifficulty = s_b.mapDifficulty
                         AND s_a.user.id = :sniperId
                         AND s_a.active = true
                        WHERE s_b.user.id = :targetId
                          AND s_b.active = true
                          AND d.active = true
                          AND (s_a.id IS NULL OR s_b.score > s_a.score)
                          AND (:allowPlayed = true OR s_a.id IS NULL)
                          AND (:allowUnplayed = true OR s_a.id IS NOT NULL)
                          AND (:categoryId IS NULL OR c.id = :categoryId)
                          AND (:overallOnly = false OR c.countForOverall = true)
                        """, countQuery = """
                        SELECT COUNT(s_b)
                        FROM Score s_b
                        JOIN s_b.mapDifficulty d
                        JOIN d.category c
                        LEFT JOIN Score s_a
                          ON s_a.mapDifficulty = s_b.mapDifficulty
                         AND s_a.user.id = :sniperId
                         AND s_a.active = true
                        WHERE s_b.user.id = :targetId
                          AND s_b.active = true
                          AND d.active = true
                          AND (s_a.id IS NULL OR s_b.score > s_a.score)
                          AND (:allowPlayed = true OR s_a.id IS NULL)
                          AND (:allowUnplayed = true OR s_a.id IS NOT NULL)
                          AND (:categoryId IS NULL OR c.id = :categoryId)
                          AND (:overallOnly = false OR c.countForOverall = true)
                        """)
        Page<Object[]> findSnipePairs(
                        @Param("sniperId") Long sniperId,
                        @Param("targetId") Long targetId,
                        @Param("categoryId") UUID categoryId,
                        @Param("overallOnly") boolean overallOnly,
                        @Param("allowPlayed") boolean allowPlayed,
                        @Param("allowUnplayed") boolean allowUnplayed,
                        Pageable pageable);

        @Query("""
                        SELECT COUNT(s) > 0 FROM Score s
                        JOIN s.user u
                        WHERE s.mapDifficulty.id = :mapDifficultyId
                          AND s.user.id IN :rivalIds
                          AND s.active = true
                          AND u.active = true AND u.banned = false
                          AND s.score < :score
                        """)
        boolean existsRivalScoreBelow(
                        @Param("mapDifficultyId") UUID mapDifficultyId,
                        @Param("rivalIds") Collection<Long> rivalIds,
                        @Param("score") Integer score);

        interface LastPlayedView {
                Long getUserId();

                Instant getLastPlayedAt();
        }

        @Query(value = """
                        SELECT u.id AS userId,
                               (SELECT MAX(COALESCE(s.time_set, s.created_at)) FROM scores s
                                WHERE s.user_id = u.id) AS lastPlayedAt
                        FROM users u
                        WHERE u.id IN (:userIds)
                        """, nativeQuery = true)
        List<LastPlayedView> findLastScoreTimes(@Param("userIds") Collection<Long> userIds);
}
