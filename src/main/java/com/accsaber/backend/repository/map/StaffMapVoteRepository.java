package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.StaffMapVote;
import com.accsaber.backend.model.entity.map.VoteType;

public interface StaffMapVoteRepository extends JpaRepository<StaffMapVote, UUID> {

        Optional<StaffMapVote> findByMapDifficultyIdAndStaffIdAndActiveTrue(UUID mapDifficultyId, UUID staffId);

        Optional<StaffMapVote> findByMapDifficultyIdAndStaffIdAndTypeAndActiveTrue(UUID mapDifficultyId, UUID staffId,
                        MapVoteAction type);

        List<StaffMapVote> findByMapDifficultyIdAndActiveTrue(UUID mapDifficultyId);

        long countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(UUID mapDifficultyId, MapVoteAction type, VoteType vote);

        @Query("SELECT v.mapDifficulty.id, v.vote, COUNT(v) FROM StaffMapVote v " +
                        "WHERE v.mapDifficulty.id IN :ids AND v.type = 'rank' AND v.active = true " +
                        "GROUP BY v.mapDifficulty.id, v.vote")
        List<Object[]> countRankVotesByDifficultyIds(@Param("ids") List<UUID> ids);

        @Query("SELECT v.mapDifficulty.id, v.criteriaVote, COUNT(v) FROM StaffMapVote v " +
                        "WHERE v.mapDifficulty.id IN :ids AND v.criteriaVote IS NOT NULL AND v.active = true " +
                        "GROUP BY v.mapDifficulty.id, v.criteriaVote")
        List<Object[]> countCriteriaVotesByDifficultyIds(@Param("ids") List<UUID> ids);

        @Query("SELECT v.mapDifficulty.id, v.criteriaVote FROM StaffMapVote v " +
                        "WHERE v.mapDifficulty.id IN :ids AND v.criteriaVoteOverride = true AND v.active = true")
        List<Object[]> findHeadCriteriaVotesByDifficultyIds(@Param("ids") List<UUID> ids);
}
