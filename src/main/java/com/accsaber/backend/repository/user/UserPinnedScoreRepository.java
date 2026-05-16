package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.user.UserPinnedScore;

public interface UserPinnedScoreRepository extends JpaRepository<UserPinnedScore, UUID> {

    @Query("""
            SELECT p FROM UserPinnedScore p
            JOIN FETCH p.score s
            JOIN FETCH s.user
            JOIN FETCH s.mapDifficulty d
            JOIN FETCH d.map
            JOIN FETCH d.category
            WHERE p.user.id = :userId AND s.active = true
            ORDER BY p.displayOrder ASC
            """)
    List<UserPinnedScore> findActiveByUserIdWithScoreGraph(@Param("userId") Long userId);

    void deleteByUser_Id(Long userId);
}
