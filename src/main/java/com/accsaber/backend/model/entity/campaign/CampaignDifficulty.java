package com.accsaber.backend.model.entity.campaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.accsaber.backend.model.entity.map.MapDifficulty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "campaign_difficulties")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDifficulty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_difficulty_id", nullable = false)
    private MapDifficulty mapDifficulty;

    @Column(name = "requirement_type", nullable = false)
    private CampaignRequirementType requirementType;

    @Column(name = "requirement_value", nullable = false, precision = 20, scale = 6)
    private BigDecimal requirementValue;

    @Column(name = "prerequisite_mode", nullable = false)
    @Builder.Default
    private CampaignPrerequisiteMode prerequisiteMode = CampaignPrerequisiteMode.OR;

    private String description;

    @Column(name = "checkpoint_label")
    private String checkpointLabel;

    @Column(name = "checkpoint_avatar_url")
    private String checkpointAvatarUrl;

    @Column(name = "checkpoint_color")
    private String checkpointColor;

    @Column(name = "border_color")
    private String borderColor;

    @Column(name = "border_shape")
    private String borderShape;

    @Column(name = "size")
    private String size;

    @Column(name = "checkpoint_size")
    private String checkpointSize;

    @Column(name = "position_x", nullable = false)
    private Integer positionX;

    @Column(name = "position_y", nullable = false)
    private Integer positionY;

    @Column(nullable = false, precision = 20, scale = 6)
    @Builder.Default
    private BigDecimal xp = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "campaignDifficulty", fetch = FetchType.LAZY)
    @Builder.Default
    private List<CampaignDifficultyPath> prerequisites = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
