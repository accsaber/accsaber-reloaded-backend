package com.accsaber.backend.repository.clan.war;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarLoan;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus;

public interface ClanWarLoanRepository extends JpaRepository<ClanWarLoan, UUID> {

    String OPEN = "(com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus.pending, "
            + "com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus.accepted)";

    @Query("SELECT COUNT(l) > 0 FROM ClanWarLoan l WHERE l.user.id = :userId AND l.status IN " + OPEN)
    boolean existsOpenByUserId(@Param("userId") Long userId);

    @Query("SELECT MAX(l.endedAt) FROM ClanWarLoan l WHERE l.user.id = :userId")
    Optional<Instant> findLastEndedAt(@Param("userId") Long userId);

    @Query("SELECT COUNT(l) FROM ClanWarLoan l WHERE l.lendingClan.id = :lendingClanId AND l.status IN " + OPEN)
    long countOpenByLendingClanId(@Param("lendingClanId") UUID lendingClanId);

    @Query("SELECT COUNT(l) FROM ClanWarLoan l WHERE l.war.id = :warId AND l.clan.id = :clanId AND l.status IN " + OPEN)
    long countOpenIntoWar(@Param("warId") UUID warId, @Param("clanId") UUID clanId);

    @Query("""
            SELECT COUNT(l) FROM ClanWarLoan l
            WHERE l.lendingClan.id = :lendingClanId AND l.clan.id = :clanId AND l.status IN
            """ + OPEN)
    long countOpenBetween(@Param("lendingClanId") UUID lendingClanId, @Param("clanId") UUID clanId);

    @Query("""
            SELECT l FROM ClanWarLoan l
            JOIN FETCH l.war JOIN FETCH l.clan JOIN FETCH l.lendingClan JOIN FETCH l.user JOIN FETCH l.offeredBy
            WHERE l.id = :id
            """)
    Optional<ClanWarLoan> findWithRefsById(@Param("id") UUID id);

    @Query("""
            SELECT l FROM ClanWarLoan l JOIN FETCH l.lendingClan
            WHERE l.war.id = :warId
              AND l.status = com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus.accepted
            """)
    List<ClanWarLoan> findAcceptedByWarId(@Param("warId") UUID warId);

    @Query("SELECT l FROM ClanWarLoan l JOIN FETCH l.war WHERE l.lendingClan.id = :lendingClanId AND l.status IN " + OPEN)
    List<ClanWarLoan> findOpenByLendingClanId(@Param("lendingClanId") UUID lendingClanId);

    @Query(value = """
            SELECT l FROM ClanWarLoan l
            JOIN FETCH l.war JOIN FETCH l.clan JOIN FETCH l.lendingClan JOIN FETCH l.user JOIN FETCH l.offeredBy
            WHERE (:warId IS NULL OR l.war.id = :warId) AND (:userId IS NULL OR l.user.id = :userId)
              AND (:status IS NULL OR l.status = :status)
            ORDER BY l.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(l) FROM ClanWarLoan l
            WHERE (:warId IS NULL OR l.war.id = :warId) AND (:userId IS NULL OR l.user.id = :userId)
              AND (:status IS NULL OR l.status = :status)
            """)
    Page<ClanWarLoan> findPage(@Param("warId") UUID warId, @Param("userId") Long userId,
            @Param("status") ClanWarLoanStatus status, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ClanWarLoan l
            SET l.status = CASE WHEN l.status = com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus.accepted
                    THEN com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus.ended
                    ELSE com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus.cancelled END,
                l.endedAt = CASE WHEN l.status = com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus.accepted
                    THEN :now ELSE l.endedAt END,
                l.resolvedAt = COALESCE(l.resolvedAt, :now)
            WHERE l.war.id = :warId AND l.status IN
            """ + OPEN)
    int closeOpenForWar(@Param("warId") UUID warId, @Param("now") Instant now);
}
