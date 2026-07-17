package com.accsaber.backend.repository.score;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.score.ScoreModifierLink;

public interface ScoreModifierLinkRepository extends JpaRepository<ScoreModifierLink, UUID> {

    List<ScoreModifierLink> findByScore_Id(UUID scoreId);

    List<ScoreModifierLink> findByScore_IdIn(Collection<UUID> scoreIds);

    @Query("SELECT l.score.id FROM ScoreModifierLink l WHERE l.score.id IN :scoreIds AND l.modifier.code = :code")
    List<UUID> findScoreIdsWithModifierCode(@Param("scoreIds") Collection<UUID> scoreIds, @Param("code") String code);
}
