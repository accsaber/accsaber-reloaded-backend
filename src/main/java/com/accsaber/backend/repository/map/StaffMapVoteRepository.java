package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.StaffMapVote;
import com.accsaber.backend.model.entity.map.VoteType;

public interface StaffMapVoteRepository extends JpaRepository<StaffMapVote, UUID> {

        Optional<StaffMapVote> findByMapDifficultyIdAndStaffIdAndActiveTrue(UUID mapDifficultyId, UUID staffId);

        @Query("SELECT v FROM StaffMapVote v JOIN FETCH v.mapDifficulty d JOIN FETCH d.map WHERE v.mapDifficulty.id = :mapDifficultyId AND v.active = true")
        List<StaffMapVote> findByMapDifficultyIdAndActiveTrue(@Param("mapDifficultyId") UUID mapDifficultyId);

        long countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(UUID mapDifficultyId, MapVoteAction type, VoteType vote);

        @Query("SELECT v.mapDifficulty.id, v.vote, COUNT(v) FROM StaffMapVote v " +
                        "WHERE v.mapDifficulty.id IN :ids AND v.type = 'rank' AND v.active = true " +
                        "GROUP BY v.mapDifficulty.id, v.vote")
        List<Object[]> countRankVotesByDifficultyIds(@Param("ids") List<UUID> ids);

        @Query("SELECT v.mapDifficulty.id, v.type, v.vote, COUNT(v) FROM StaffMapVote v " +
                        "WHERE v.mapDifficulty.id IN :ids AND v.type IN ('reweight', 'unrank') AND v.active = true " +
                        "GROUP BY v.mapDifficulty.id, v.type, v.vote")
        List<Object[]> countReweightAndUnrankVotesByDifficultyIds(@Param("ids") List<UUID> ids);

        @Query("SELECT v.mapDifficulty.id, v.criteriaVote, COUNT(v) FROM StaffMapVote v " +
                        "WHERE v.mapDifficulty.id IN :ids AND v.criteriaVote IS NOT NULL AND v.active = true " +
                        "GROUP BY v.mapDifficulty.id, v.criteriaVote")
        List<Object[]> countCriteriaVotesByDifficultyIds(@Param("ids") List<UUID> ids);

        @Query("SELECT v.mapDifficulty.id, v.criteriaVote FROM StaffMapVote v " +
                        "WHERE v.mapDifficulty.id IN :ids AND v.criteriaVoteOverride = true AND v.active = true")
        List<Object[]> findHeadCriteriaVotesByDifficultyIds(@Param("ids") List<UUID> ids);

        @Query("SELECT v FROM StaffMapVote v JOIN FETCH v.mapDifficulty d JOIN FETCH d.map WHERE v.active = true")
        Page<StaffMapVote> findAllActiveWithMap(Pageable pageable);
}
