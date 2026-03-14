package com.accsaber.backend.repository.milestone;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.milestone.MilestoneCompletionStats;

public interface MilestoneCompletionStatsRepository extends JpaRepository<MilestoneCompletionStats, UUID> {

    Optional<MilestoneCompletionStats> findByMilestoneId(UUID milestoneId);

    @Modifying
    @Transactional
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY milestone_completion_stats", nativeQuery = true)
    void refresh();
}
