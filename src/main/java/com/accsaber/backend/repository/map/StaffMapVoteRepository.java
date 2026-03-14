package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.StaffMapVote;
import com.accsaber.backend.model.entity.map.VoteType;

public interface StaffMapVoteRepository extends JpaRepository<StaffMapVote, UUID> {

    Optional<StaffMapVote> findByMapDifficultyIdAndStaffIdAndActiveTrue(UUID mapDifficultyId, UUID staffId);

    List<StaffMapVote> findByMapDifficultyIdAndActiveTrue(UUID mapDifficultyId);

    long countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(UUID mapDifficultyId, MapVoteAction type, VoteType vote);
}
