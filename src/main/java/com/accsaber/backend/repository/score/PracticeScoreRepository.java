package com.accsaber.backend.repository.score;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.score.PracticeScore;

@Repository
public interface PracticeScoreRepository extends JpaRepository<PracticeScore, UUID> {

    List<PracticeScore> findAllByOrderByScoreDesc(Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO practice_scores (id, name, score, level, accuracy, bad_cuts, bomb_hits, played_at, created_at)
            VALUES (:id, :name, :score, :level, :accuracy, :badCuts, :bombHits, :playedAt, NOW())
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("score") int score,
            @Param("level") int level,
            @Param("accuracy") double accuracy,
            @Param("badCuts") int badCuts,
            @Param("bombHits") int bombHits,
            @Param("playedAt") Instant playedAt);
}
