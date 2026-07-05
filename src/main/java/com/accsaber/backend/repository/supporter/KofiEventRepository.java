package com.accsaber.backend.repository.supporter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.supporter.KofiEvent;

@Repository
public interface KofiEventRepository extends JpaRepository<KofiEvent, UUID> {

        Optional<KofiEvent> findByKofiTransactionId(String kofiTransactionId);

        @Query("SELECT e FROM KofiEvent e WHERE e.claimedUser IS NULL "
                        + "AND LOWER(e.email) = LOWER(:email) "
                        + "AND e.receivedAt >= :since "
                        + "ORDER BY e.receivedAt DESC")
        List<KofiEvent> findUnclaimedByEmailSince(@Param("email") String email, @Param("since") Instant since);

        @Query("SELECT e FROM KofiEvent e WHERE e.claimedUser IS NULL "
                        + "AND e.tierName IS NOT NULL "
                        + "AND e.receivedAt >= :since "
                        + "ORDER BY e.receivedAt DESC")
        List<KofiEvent> findUnclaimedSince(@Param("since") Instant since);

        List<KofiEvent> findByClaimedUser_IdOrderByClaimedAtDesc(Long userId);

        @Query("SELECT e.claimedUser.id FROM KofiEvent e "
                        + "WHERE e.claimedUser IS NOT NULL "
                        + "AND e.email IS NOT NULL "
                        + "AND LOWER(e.email) = LOWER(:email) "
                        + "ORDER BY e.claimedAt DESC")
        List<Long> findClaimedUserIdsByEmail(@Param("email") String email,
                        org.springframework.data.domain.Pageable pageable);
}
