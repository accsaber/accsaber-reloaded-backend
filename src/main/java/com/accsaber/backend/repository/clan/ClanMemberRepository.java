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

    @Query("SELECT m.user.id FROM ClanMember m WHERE m.clan.id = :clanId AND m.leftAt IS NULL AND m.role IN :roles")
    List<Long> findOpenUserIdsByRoles(@Param("clanId") UUID clanId, @Param("roles") Collection<ClanRole> roles);

    @Query("SELECT m FROM ClanMember m JOIN FETCH m.clan WHERE m.user.id = :userId AND m.leftAt IS NULL")
    Optional<ClanMember> findOpenByUserId(@Param("userId") Long userId);

    @Query("SELECT m FROM ClanMember m JOIN FETCH m.clan c WHERE m.leftAt IS NULL AND c.active")
    List<ClanMember> findAllOpenInActiveClans();

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

    interface MemberSeasonStatsView {
        Long getUserId();

        double getPlayXp();

        long getHits();

        long getBreaks();
    }

    @Query(value = """
            SELECT m.user_id AS userId,
                   COALESCE((SELECT SUM(g.amount) FROM scores s
                             JOIN clan_xp_grants g ON g.clan_id = m.clan_id AND g.source = 'play'
                              AND g.source_id = CAST(s.id AS TEXT)
                             WHERE s.user_id = m.user_id
                               AND COALESCE(s.time_set, s.created_at) >= GREATEST(m.joined_at, :from)), 0) AS playXp,
                   war.hits AS hits, war.breaks AS breaks
            FROM clan_members m
            CROSS JOIN LATERAL (
                SELECT COUNT(*) AS hits, COUNT(*) FILTER (WHERE h.broke) AS breaks
                FROM clan_war_hits h
                JOIN clan_wars w ON w.id = h.war_id
                JOIN clan_war_participants p ON p.war_id = h.war_id AND p.user_id = h.attacker_user_id
                WHERE h.attacker_user_id = m.user_id AND p.clan_id = m.clan_id AND w.season_id = :seasonId) war
            WHERE m.clan_id = :clanId AND m.left_at IS NULL AND m.user_id IN (:userIds)
            """, nativeQuery = true)
    List<MemberSeasonStatsView> findSeasonStats(@Param("clanId") UUID clanId,
            @Param("userIds") Collection<Long> userIds, @Param("seasonId") UUID seasonId,
            @Param("from") Instant from);

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

    interface MemberSkillView {
        Long getUserId();

        double getSkill();
    }

    @Query("""
            SELECT m.user.id AS userId, COALESCE(s.skillLevel, 0.0) AS skill
            FROM ClanMember m LEFT JOIN UserCategorySkill s ON s.user = m.user AND s.category.id = :overallId
            WHERE m.clan.id = :clanId AND m.leftAt IS NULL
            """)
    List<MemberSkillView> findOpenMemberSkillsByClan(@Param("clanId") UUID clanId, @Param("overallId") UUID overallId);

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
