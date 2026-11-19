package com.accsaber.backend.model.entity.map;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "map_difficulty_subscriptions")
@IdClass(MapDifficultySubscription.Key.class)
@Getter
@NoArgsConstructor
public class MapDifficultySubscription {

    @Id
    @Column(name = "map_difficulty_id", nullable = false)
    private UUID mapDifficultyId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        private UUID mapDifficultyId;

        private Long userId;
    }
}
