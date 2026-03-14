package com.accsaber.backend.model.entity.map;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.CriteriaStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "map_difficulties")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapDifficulty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id", nullable = false)
    private Map map;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private String characteristic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_version_id")
    private MapDifficulty previousVersion;

    @Column(name = "ss_leaderboard_id")
    private String ssLeaderboardId;

    @Column(name = "bl_leaderboard_id")
    private String blLeaderboardId;

    @Column(nullable = false)
    private MapDifficultyStatus status;

    @Column(name = "max_score")
    private Integer maxScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(name = "ranked_at")
    private Instant rankedAt;

    @Column(name = "last_updated_by")
    private UUID lastUpdatedBy;

    @Column(name = "criteria_status", nullable = false)
    @Builder.Default
    private CriteriaStatus criteriaStatus = CriteriaStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
