package com.accsaber.backend.model.entity.campaign;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.accsaber.backend.model.entity.Modifier;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "campaign_difficulty_modifiers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDifficultyModifier {

    @EmbeddedId
    private CampaignDifficultyModifierId id;

    @MapsId("campaignDifficultyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_difficulty_id", nullable = false)
    private CampaignDifficulty campaignDifficulty;

    @MapsId("modifierId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modifier_id", nullable = false)
    private Modifier modifier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignModifierRequirement requirement;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Embeddable
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignDifficultyModifierId implements Serializable {

        @Column(name = "campaign_difficulty_id")
        private UUID campaignDifficultyId;

        @Column(name = "modifier_id")
        private UUID modifierId;

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CampaignDifficultyModifierId other))
                return false;
            return Objects.equals(campaignDifficultyId, other.campaignDifficultyId)
                    && Objects.equals(modifierId, other.modifierId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(campaignDifficultyId, modifierId);
        }
    }
}
