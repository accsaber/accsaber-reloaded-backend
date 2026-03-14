package com.accsaber.backend.model.entity.score;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.user.User;

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
@Table(name = "scores")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_difficulty_id", nullable = false)
    private MapDifficulty mapDifficulty;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "score_no_mods", nullable = false)
    private Integer scoreNoMods;

    @Column(nullable = false)
    private Integer rank;

    @Column(name = "rank_when_set", nullable = false)
    private Integer rankWhenSet;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal ap;

    @Column(name = "weighted_ap", nullable = false, precision = 20, scale = 6)
    private BigDecimal weightedAp;

    @Column(name = "bl_score_id")
    private Long blScoreId;

    @Column(name = "wall_hits")
    private Integer wallHits;

    @Column(name = "bomb_hits")
    private Integer bombHits;

    private Integer pauses;

    @Column(name = "streak_115")
    private Integer streak115;

    @Column(name = "play_count")
    private Integer playCount;

    @Column(name = "max_combo")
    private Integer maxCombo;

    @Column(name = "bad_cuts")
    private Integer badCuts;

    private Integer misses;

    private String hmd;

    @Column(name = "time_set")
    private Instant timeSet;

    @Column(name = "reweight_derivative")
    @Builder.Default
    private boolean reweightDerivative = false;

    @Column(name = "xp_gained", precision = 20, scale = 6)
    private BigDecimal xpGained;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    private Score supersedes;

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
