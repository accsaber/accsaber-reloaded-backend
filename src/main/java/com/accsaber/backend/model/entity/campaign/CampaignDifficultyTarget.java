package com.accsaber.backend.model.entity.campaign;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
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
@Table(name = "campaign_difficulty_targets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDifficultyTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_difficulty_id", nullable = false)
    private CampaignDifficulty campaignDifficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false)
    private CampaignRequirementType requirementType;

    @Column(name = "requirement_value")
    private Double requirementValue;

    @Column(name = "requirement_value_max")
    private Double requirementValueMax;

    @Column(nullable = false)
    @Builder.Default
    private Integer ordinal = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
