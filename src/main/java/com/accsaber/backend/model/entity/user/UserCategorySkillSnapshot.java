package com.accsaber.backend.model.entity.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "user_category_skill_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCategorySkillSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "skill_level", nullable = false, precision = 5, scale = 2)
    private BigDecimal skillLevel;

    @Column(name = "rank_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal rankScore;

    @Column(name = "sustained_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal sustainedScore;

    @Column(name = "peak_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal peakScore;

    @Column(name = "combined_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal combinedScore;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;
}
