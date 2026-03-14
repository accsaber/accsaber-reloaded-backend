package com.accsaber.backend.repository.score;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.score.ScoreModifierLink;

public interface ScoreModifierLinkRepository extends JpaRepository<ScoreModifierLink, UUID> {

    List<ScoreModifierLink> findByScore_Id(UUID scoreId);
}
