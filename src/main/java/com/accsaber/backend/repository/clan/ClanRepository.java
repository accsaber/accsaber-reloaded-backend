package com.accsaber.backend.repository.clan;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.Clan;

import jakarta.persistence.LockModeType;

public interface ClanRepository extends JpaRepository<Clan, UUID> {

    Optional<Clan> findByIdAndActiveTrue(UUID id);

    Optional<Clan> findBySlugAndActiveTrue(String slug);

    boolean existsBySlugAndActiveTrue(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Clan c WHERE c.id = :id AND c.active = true")
    Optional<Clan> findByIdAndActiveTrueForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT c FROM Clan c
            WHERE c.active = true
              AND (CAST(:search AS string) IS NULL
                   OR search_normalize(c.name) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
                   OR c.tag LIKE CONCAT('%', UPPER(CAST(:search AS string)), '%'))
            """)
    Page<Clan> search(@Param("search") String search, Pageable pageable);

    @Query("SELECT c.id FROM Clan c WHERE c.active = true ORDER BY c.id")
    Page<UUID> findActiveIds(Pageable pageable);
}
