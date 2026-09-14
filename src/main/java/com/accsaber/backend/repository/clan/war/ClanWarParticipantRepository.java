package com.accsaber.backend.repository.clan.war;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.repository.clan.ClanMemberRepository;

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

    @Query("SELECT p FROM ClanWarParticipant p WHERE p.war.id = :warId AND p.leftAt IS NULL")
    List<ClanWarParticipant> findActiveByWarId(@Param("warId") UUID warId);

    @Query(value = """
            SELECT s.user_id AS userId, s.skill_level AS skill
            FROM user_category_skills s
            JOIN categories c ON c.id = s.category_id AND c.code = 'overall' AND c.active
            WHERE s.user_id IN (:userIds)
            """, nativeQuery = true)
    List<ClanMemberRepository.MemberSkillView> findOverallSkills(@Param("userIds") Collection<Long> userIds);
}
