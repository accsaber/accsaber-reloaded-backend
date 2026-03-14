package com.accsaber.backend.repository.milestone;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.milestone.MilestoneSet;

public interface MilestoneSetRepository extends JpaRepository<MilestoneSet, UUID> {

    List<MilestoneSet> findByActiveTrue();

    Page<MilestoneSet> findByActiveTrue(Pageable pageable);

    Optional<MilestoneSet> findByIdAndActiveTrue(UUID id);
}
