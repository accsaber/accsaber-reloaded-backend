package com.accsaber.backend.repository.score;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.dto.projection.ScoreModifierRow;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;

public interface ScoreModifierLinkRepository extends JpaRepository<ScoreModifierLink, UUID> {

    List<ScoreModifierLink> findByScore_Id(UUID scoreId);

    List<ScoreModifierLink> findByScore_IdIn(Collection<UUID> scoreIds);

    @Modifying
    @Query(value = """
            INSERT INTO score_modifier_links (score_id, modifier_id)
            SELECT CAST(:toScoreId AS uuid), modifier_id FROM score_modifier_links WHERE score_id = :fromScoreId
            """, nativeQuery = true)
    void copyLinks(@Param("fromScoreId") UUID fromScoreId, @Param("toScoreId") UUID toScoreId);

    @Query("""
            SELECT new com.accsaber.backend.model.dto.projection.ScoreModifierRow(
                l.score.id, l.modifier.id, l.modifier.code)
            FROM ScoreModifierLink l
            WHERE l.score.id IN :scoreIds
            """)
    List<ScoreModifierRow> findModifierRows(@Param("scoreIds") Collection<UUID> scoreIds);
}
