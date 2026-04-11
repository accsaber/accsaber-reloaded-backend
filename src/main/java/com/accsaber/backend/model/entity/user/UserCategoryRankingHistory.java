package com.accsaber.backend.model.entity.user;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.Category;

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
@Table(name = "user_category_ranking_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCategoryRankingHistory {

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

    @Builder.Default
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();
}
