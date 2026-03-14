package com.accsaber.backend.model.entity.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.score.Score;

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
@Table(name = "user_category_statistics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCategoryStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    private Integer ranking;

    @Column(name = "country_ranking")
    private Integer countryRanking;

    @Column(nullable = false, precision = 20, scale = 6)
    @Builder.Default
    private BigDecimal ap = BigDecimal.ZERO;

    @Column(name = "average_acc", precision = 20, scale = 6)
    private BigDecimal averageAcc;

    @Column(name = "average_ap", precision = 20, scale = 6)
    private BigDecimal averageAp;

    @Column(name = "ranked_plays", nullable = false)
    @Builder.Default
    private Integer rankedPlays = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "top_play_id")
    private Score topPlay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    private UserCategoryStatistics supersedes;

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
