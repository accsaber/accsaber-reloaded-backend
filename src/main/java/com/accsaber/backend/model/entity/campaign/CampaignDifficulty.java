package com.accsaber.backend.model.entity.campaign;

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
    @JoinColumn(name = "map_difficulty_id")
    private MapDifficulty mapDifficulty;

    @Column(name = "requirement_type")
    private CampaignRequirementType requirementType;

    @Column(name = "requirement_value")
    private Double requirementValue;

    @Column(name = "requirement_value_max")
    private Double requirementValueMax;

    @Column(nullable = false)
    @Builder.Default
    private boolean barrier = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean terminal = false;

    @Column(name = "barrier_condition_type")
    private BarrierConditionType barrierConditionType;

    @Column(name = "barrier_condition_value")
    private Double barrierConditionValue;

    @Column(name = "barrier_condition_value_max")
    private Double barrierConditionValueMax;

    @Column(name = "prerequisite_mode", nullable = false)
    @Builder.Default
    private CampaignPrerequisiteMode prerequisiteMode = CampaignPrerequisiteMode.OR;

    @Column(name = "target_mode", nullable = false)
    @Builder.Default
    private CampaignPrerequisiteMode targetMode = CampaignPrerequisiteMode.AND;

    private String description;

    @Column(name = "checkpoint_label")
    private String checkpointLabel;

    @Column(name = "checkpoint_label_position")
    private CampaignLabelPosition checkpointLabelPosition;

    @Column(name = "checkpoint_avatar_url")
    private String checkpointAvatarUrl;

    @Column(name = "checkpoint_color")
    private String checkpointColor;

    @Column(name = "border_color")
    private String borderColor;

    @Column(name = "border_shape")
    private String borderShape;

    @Column(name = "node_border_url")
    private String nodeBorderUrl;

    @Column(name = "node_border_layer", nullable = false)
    @Builder.Default
    private CampaignNodeBorderLayer nodeBorderLayer = CampaignNodeBorderLayer.ABOVE;

    @Column(name = "size")
    private Integer size;

    @Column(name = "checkpoint_size")
    private Integer checkpointSize;

    @Column(name = "position_x", nullable = false)
    private double positionX;

    @Column(name = "position_y", nullable = false)
    private double positionY;

    @Column(nullable = false)
    @Builder.Default
    private double xp = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "requirement_dirty", nullable = false)
    @Builder.Default
    private boolean requirementDirty = false;

    @OneToMany(mappedBy = "campaignDifficulty", fetch = FetchType.LAZY)
    @Builder.Default
    private List<CampaignDifficultyPath> prerequisites = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CampaignPrerequisiteMode pathMode() {
        if (barrier && barrierConditionType == BarrierConditionType.COMPLETION_COUNT) {
            return CampaignPrerequisiteMode.OR;
        }
        return prerequisiteMode != null ? prerequisiteMode : CampaignPrerequisiteMode.OR;
    }
}
