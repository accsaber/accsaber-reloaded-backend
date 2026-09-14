package com.accsaber.backend.repository.clan.war;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarHit;

public interface ClanWarHitRepository extends JpaRepository<ClanWarHit, UUID> {

    interface VictimScoreView {
        UUID getScoreId();

        Long getUserId();

        int getScore();
    }

    @Query(value = """
            SELECT s.id AS scoreId, s.user_id AS userId, s.score AS score
            FROM scores s
            WHERE s.map_difficulty_id = :mapDifficultyId AND s.active AND s.user_id IN (:userIds)
            """, nativeQuery = true)
    List<VictimScoreView> findActiveScores(@Param("mapDifficultyId") UUID mapDifficultyId,
            @Param("userIds") Collection<Long> userIds);

    boolean existsByWar_IdAndAttacker_IdAndVictimScore_Id(UUID warId, Long attackerId, UUID victimScoreId);

    boolean existsByWar_IdAndAttacker_IdAndVictim_IdAndMapDifficulty_IdAndVictimScoreIsNull(UUID warId,
            Long attackerId, Long victimId, UUID mapDifficultyId);

    long countByWar_IdAndVictim_IdAndVictimCycle(UUID warId, Long victimId, int victimCycle);

    List<ClanWarHit> findByWar_IdAndVictim_IdAndVictimCycle(UUID warId, Long victimId, int victimCycle);

    @Query(value = """
            SELECT h FROM ClanWarHit h
            JOIN FETCH h.attacker JOIN FETCH h.victim
            WHERE h.war.id = :warId
            ORDER BY h.createdAt DESC
            """,
            countQuery = "SELECT COUNT(h) FROM ClanWarHit h WHERE h.war.id = :warId")
    Page<ClanWarHit> findPageByWarId(@Param("warId") UUID warId, Pageable pageable);
}
