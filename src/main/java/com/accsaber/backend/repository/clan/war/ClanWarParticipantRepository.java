package com.accsaber.backend.repository.clan.war;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;

public interface ClanWarParticipantRepository extends JpaRepository<ClanWarParticipant, ClanWarParticipant.Key> {

    @Query("SELECT p FROM ClanWarParticipant p WHERE p.war.id = :warId")
    List<ClanWarParticipant> findByWarId(@Param("warId") UUID warId);

    @Query(value = """
            SELECT p FROM ClanWarParticipant p
            JOIN FETCH p.user LEFT JOIN FETCH p.duelTarget
            WHERE p.war.id = :warId
            ORDER BY p.leftAt ASC NULLS FIRST, p.contribution DESC, p.joinedAt ASC
            """,
            countQuery = "SELECT COUNT(p) FROM ClanWarParticipant p WHERE p.war.id = :warId")
    Page<ClanWarParticipant> findPageByWarId(@Param("warId") UUID warId, Pageable pageable);
}
