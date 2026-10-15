package com.accsaber.backend.repository.supporter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.supporter.KofiEvent;

public interface KofiEventRepository extends JpaRepository<KofiEvent, UUID> {

        Optional<KofiEvent> findByKofiTransactionId(String kofiTransactionId);

        @Query("SELECT e FROM KofiEvent e WHERE e.claimedUser IS NULL "
                        + "AND e.tierName IS NOT NULL "
                        + "AND e.receivedAt >= :since "
                        + "ORDER BY e.receivedAt DESC")
        List<KofiEvent> findUnclaimedSince(@Param("since") Instant since);

        String FILTERED_WHERE = """
                        WHERE (:status = 'all'
                             OR (:status = 'unclaimed' AND e.claimedUser IS NULL)
                             OR (:status = 'claimed' AND e.claimedUser IS NOT NULL))
                        AND (:userId IS NULL OR u.id = :userId)
                        AND (CAST(:search AS string) IS NULL
                             OR LOWER(e.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                             OR LOWER(e.fromName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
                        """;

        @Query(value = "SELECT e FROM KofiEvent e LEFT JOIN FETCH e.claimedUser u " + FILTERED_WHERE,
                        countQuery = "SELECT count(e) FROM KofiEvent e LEFT JOIN e.claimedUser u " + FILTERED_WHERE)
        Page<KofiEvent> findFiltered(@Param("status") String status,
                        @Param("userId") Long userId,
                        @Param("search") String search,
                        org.springframework.data.domain.Pageable pageable);

        @Query("SELECT e.claimedUser.id FROM KofiEvent e "
                        + "WHERE e.claimedUser IS NOT NULL "
                        + "AND e.email IS NOT NULL "
                        + "AND LOWER(e.email) = LOWER(:email) "
                        + "ORDER BY e.claimedAt DESC")
        List<Long> findClaimedUserIdsByEmail(@Param("email") String email,
                        org.springframework.data.domain.Pageable pageable);
}
