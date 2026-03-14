package com.accsaber.backend.model.entity.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "map_difficulty_statistics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapDifficultyStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_difficulty_id", nullable = false)
    private MapDifficulty mapDifficulty;

    @Column(name = "max_ap", nullable = false)
    private BigDecimal maxAp;

    @Column(name = "min_ap", nullable = false)
    private BigDecimal minAp;

    @Column(name = "average_ap", nullable = false)
    private BigDecimal averageAp;

    @Column(name = "total_scores", nullable = false)
    @Builder.Default
    private int totalScores = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    private MapDifficultyStatistics supersedes;

    @Column(name = "supersedes_reason")
    private String supersedesReason;

    @Column(name = "supersedes_author")
    private Long supersedesAuthor;

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
