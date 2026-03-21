package com.accsaber.backend.repository.milestone;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.milestone.MilestonePrerequisiteLink;

public interface MilestonePrerequisiteLinkRepository extends JpaRepository<MilestonePrerequisiteLink, UUID> {

        List<MilestonePrerequisiteLink> findByMilestone_IdAndActiveTrue(UUID milestoneId);

        List<MilestonePrerequisiteLink> findByPrerequisiteMilestone_IdAndActiveTrue(UUID prerequisiteMilestoneId);

        boolean existsByMilestone_IdAndPrerequisiteMilestone_IdAndActiveTrue(UUID milestoneId,
                        UUID prerequisiteMilestoneId);

        @Query("""
                        SELECT mpl FROM MilestonePrerequisiteLink mpl
                        JOIN FETCH mpl.prerequisiteMilestone
                        WHERE mpl.milestone.milestoneSet.id = :setId AND mpl.active = true
                        """)
        List<MilestonePrerequisiteLink> findBySetIdWithPrerequisites(@Param("setId") UUID setId);
}
