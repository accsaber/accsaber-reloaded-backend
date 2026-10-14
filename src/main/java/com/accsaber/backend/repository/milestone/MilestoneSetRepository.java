package com.accsaber.backend.repository.milestone;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.accsaber.backend.model.entity.milestone.MilestoneSet;

public interface MilestoneSetRepository extends JpaRepository<MilestoneSet, UUID> {

    @Query("""
            SELECT ms FROM MilestoneSet ms
            WHERE ms.active = true
            AND EXISTS (
                SELECT m FROM Milestone m
                WHERE m.milestoneSet = ms AND m.active = true AND m.status = 'ACTIVE'
            )
            """)
    Page<MilestoneSet> findByActiveTrueWithActiveMilestones(Pageable pageable);

    Optional<MilestoneSet> findByIdAndActiveTrue(UUID id);
}
