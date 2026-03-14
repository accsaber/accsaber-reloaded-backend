package com.accsaber.backend.model.entity.milestone;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.util.MilestoneQuerySpecConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "milestones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_id", nullable = false)
    private MilestoneSet milestoneSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MilestoneTier tier = MilestoneTier.bronze;

    @Column(nullable = false, precision = 20, scale = 6)
    @Builder.Default
    private BigDecimal xp = BigDecimal.ZERO;

    @Column(name = "query_spec", nullable = false, columnDefinition = "jsonb")
    @Convert(converter = MilestoneQuerySpecConverter.class)
    private MilestoneQuerySpec querySpec;

    @Column(name = "target_value", nullable = false, precision = 20, scale = 6)
    private BigDecimal targetValue;

    @Column(nullable = false)
    @Builder.Default
    private String comparison = "GTE";

    @Column(name = "bl_exclusive", nullable = false)
    @Builder.Default
    private boolean blExclusive = false;

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
