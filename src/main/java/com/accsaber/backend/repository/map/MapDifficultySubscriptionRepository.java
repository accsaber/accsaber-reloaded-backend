package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.MapDifficultySubscription;

public interface MapDifficultySubscriptionRepository
        extends JpaRepository<MapDifficultySubscription, MapDifficultySubscription.Key> {

    @Query("SELECT s.userId FROM MapDifficultySubscription s WHERE s.mapDifficultyId = :difficultyId")
    List<Long> findUserIds(@Param("difficultyId") UUID difficultyId);

    @Modifying
    @Query(value = """
            INSERT INTO map_difficulty_subscriptions (map_difficulty_id, user_id)
            VALUES (:difficultyId, :userId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void subscribe(@Param("difficultyId") UUID difficultyId, @Param("userId") Long userId);
}
