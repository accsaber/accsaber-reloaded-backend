package com.accsaber.backend.repository.clan;

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

import com.accsaber.backend.model.entity.clan.ClanLeaveReason;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;

public interface ClanMemberRepository extends JpaRepository<ClanMember, UUID> {

    @Query("SELECT m.user.id FROM ClanMember m WHERE m.clan.id = :clanId AND m.leftAt IS NULL ORDER BY m.joinedAt")
    List<Long> findOpenUserIds(@Param("clanId") UUID clanId);

    @Query("SELECT m FROM ClanMember m JOIN FETCH m.clan WHERE m.user.id = :userId AND m.leftAt IS NULL")
    Optional<ClanMember> findOpenByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT m FROM ClanMember m JOIN FETCH m.user
            WHERE m.clan.id = :clanId AND m.user.id = :userId AND m.leftAt IS NULL
            """)
    Optional<ClanMember> findOpenByClanIdAndUserId(@Param("clanId") UUID clanId, @Param("userId") Long userId);

    Optional<ClanMember> findFirstByUser_IdOrderByJoinedAtDesc(Long userId);

    long countByClan_IdAndLeftAtIsNull(UUID clanId);

    long countByClan_IdAndRoleAndLeftAtIsNull(UUID clanId, ClanRole role);

    @Query(value = """
            SELECT m FROM ClanMember m JOIN FETCH m.user
            WHERE m.clan.id = :clanId AND m.leftAt IS NULL
            ORDER BY CASE m.role
                         WHEN com.accsaber.backend.model.entity.clan.ClanRole.founder THEN 0
                         WHEN com.accsaber.backend.model.entity.clan.ClanRole.commander THEN 1
                         WHEN com.accsaber.backend.model.entity.clan.ClanRole.officer THEN 2
                         ELSE 3
                     END,
                     m.joinedAt ASC
            """,
            countQuery = "SELECT COUNT(m) FROM ClanMember m WHERE m.clan.id = :clanId AND m.leftAt IS NULL")
    Page<ClanMember> findRoster(@Param("clanId") UUID clanId, Pageable pageable);

    interface MemberCountView {
        UUID getClanId();

        long getMembers();
    }

    @Query("""
            SELECT m.clan.id AS clanId, COUNT(m) AS members FROM ClanMember m
            WHERE m.clan.id IN :clanIds AND m.leftAt IS NULL
            GROUP BY m.clan.id
            """)
    List<MemberCountView> countOpenByClanIds(@Param("clanIds") Collection<UUID> clanIds);

    @Query("""
            SELECT m FROM ClanMember m JOIN FETCH m.user
            WHERE m.clan.id IN :clanIds
              AND m.role = com.accsaber.backend.model.entity.clan.ClanRole.founder
              AND m.leftAt IS NULL
            """)
    List<ClanMember> findOpenFounders(@Param("clanIds") Collection<UUID> clanIds);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ClanMember m SET m.leftAt = :now, m.leaveReason = :reason
            WHERE m.clan.id = :clanId AND m.leftAt IS NULL
            """)
    int closeOpenByClanId(@Param("clanId") UUID clanId, @Param("reason") ClanLeaveReason reason,
            @Param("now") Instant now);

    interface ClanSkillView {
        UUID getClanId();

        double getSkill();
    }

    @Query("""
            SELECT m.clan.id AS clanId, s.skillLevel AS skill
            FROM ClanMember m, UserCategorySkill s
            WHERE s.user = m.user AND s.category.id = :overallId
              AND m.leftAt IS NULL AND m.clan.id IN :clanIds
            """)
    List<ClanSkillView> findOpenMemberSkills(@Param("clanIds") Collection<UUID> clanIds,
            @Param("overallId") UUID overallId);

    @Query(value = """
            SELECT pair.clan_id AS clanId, MAX(s.skill_level) AS skill
            FROM (
                SELECT a.clan_a_id AS clan_id, a.clan_b_id AS ally_id FROM clan_alliances a WHERE a.status = 'active'
                UNION ALL
                SELECT a.clan_b_id, a.clan_a_id FROM clan_alliances a WHERE a.status = 'active'
            ) pair
            JOIN clan_members m ON m.clan_id = pair.ally_id AND m.left_at IS NULL
            JOIN user_category_skills s ON s.user_id = m.user_id AND s.category_id = :overallId
            WHERE pair.clan_id IN (:clanIds)
              AND EXISTS (
                  SELECT 1 FROM clan_wars w JOIN clan_seasons cs ON cs.id = w.season_id
                  WHERE (w.attacker_clan_id = pair.ally_id OR w.defender_clan_id = pair.ally_id)
                    AND cs.starts_at <= :now AND cs.ends_at > :now)
            GROUP BY pair.clan_id, pair.ally_id
            """, nativeQuery = true)
    List<ClanSkillView> findFoughtAllyTopSkills(@Param("clanIds") Collection<UUID> clanIds,
            @Param("overallId") UUID overallId, @Param("now") Instant now);
}
