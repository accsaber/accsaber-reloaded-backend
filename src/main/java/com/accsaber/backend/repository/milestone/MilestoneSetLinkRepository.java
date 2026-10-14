package com.accsaber.backend.repository.milestone;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.milestone.MilestoneSetLink;

public interface MilestoneSetLinkRepository extends JpaRepository<MilestoneSetLink, UUID> {

        @Query("""
                        SELECT msl FROM MilestoneSetLink msl
                        JOIN FETCH msl.milestoneSet
                        JOIN FETCH msl.group
                        WHERE msl.active = true
                        ORDER BY msl.sortOrder
                        """)
        List<MilestoneSetLink> findAllActiveWithSets();

        @Query("""
                        SELECT msl FROM MilestoneSetLink msl
                        JOIN FETCH msl.milestoneSet
                        JOIN FETCH msl.group
                        WHERE msl.milestoneSet.id = :setId AND msl.active = true
                        ORDER BY msl.sortOrder
                        """)
        List<MilestoneSetLink> findBySetIdWithGroup(@Param("setId") UUID setId);

        boolean existsByGroup_IdAndMilestoneSet_IdAndActiveTrue(UUID groupId, UUID setId);
}
