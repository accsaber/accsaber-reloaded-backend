package com.accsaber.backend.repository.supporter;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.supporter.SupporterAccount;

@Repository
public interface SupporterAccountRepository extends JpaRepository<SupporterAccount, Long> {

    @Query("SELECT a FROM SupporterAccount a WHERE a.currentTier IS NOT NULL AND a.lastDebitAt <= :cutoff")
    List<SupporterAccount> findActiveTiersDueForDebit(@Param("cutoff") Instant cutoff);

    List<SupporterAccount> findByCurrentTierIsNotNull();

    @Query(value = "SELECT a FROM SupporterAccount a JOIN FETCH a.user u WHERE a.lifetimeSupportedCents > 0 "
            + "AND (:status = 'all' "
            + "     OR (:status = 'active' AND a.currentTier IS NOT NULL) "
            + "     OR (:status = 'past' AND a.currentTier IS NULL))",
            countQuery = "SELECT count(a) FROM SupporterAccount a WHERE a.lifetimeSupportedCents > 0 "
            + "AND (:status = 'all' "
            + "     OR (:status = 'active' AND a.currentTier IS NOT NULL) "
            + "     OR (:status = 'past' AND a.currentTier IS NULL))")
    Page<SupporterAccount> findCredits(@Param("status") String status, Pageable pageable);
}
