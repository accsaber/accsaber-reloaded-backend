package com.accsaber.backend.repository.clan;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanSeason;

import jakarta.persistence.LockModeType;

public interface ClanSeasonRepository extends JpaRepository<ClanSeason, UUID> {

    Optional<ClanSeason> findBySlug(String slug);

    Page<ClanSeason> findAllByOrderByStartsAtDesc(Pageable pageable);

    Optional<ClanSeason> findTopByOrderByEndsAtDesc();

    @Query("""
            SELECT s FROM ClanSeason s
            WHERE s.closedAt IS NULL AND s.startsAt <= :now AND s.endsAt > :now
            """)
    Optional<ClanSeason> findCurrent(@Param("now") Instant now);

    @Query("SELECT COUNT(s) > 0 FROM ClanSeason s WHERE s.closedAt IS NULL AND s.endsAt > :now")
    boolean existsOpenUntilAfter(@Param("now") Instant now);

    @Query("SELECT s.id FROM ClanSeason s WHERE s.closedAt IS NULL AND s.endsAt <= :now ORDER BY s.endsAt")
    List<UUID> findEndedUnclosedIds(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ClanSeason s WHERE s.id = :id")
    Optional<ClanSeason> findByIdForUpdate(@Param("id") UUID id);
}
