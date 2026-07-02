package com.accsaber.backend.model.entity.campaign;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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
@Table(name = "campaign_barrier_affected_difficulties")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignBarrierAffectedDifficulty {

    @EmbeddedId
    private CampaignBarrierAffectedDifficultyId id;

    @MapsId("barrierId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barrier_id", nullable = false)
    private CampaignDifficulty barrier;

    @MapsId("campaignDifficultyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_difficulty_id", nullable = false)
    private CampaignDifficulty affectedDifficulty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Embeddable
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignBarrierAffectedDifficultyId implements Serializable {

        @Column(name = "barrier_id")
        private UUID barrierId;

        @Column(name = "campaign_difficulty_id")
        private UUID campaignDifficultyId;

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CampaignBarrierAffectedDifficultyId other))
                return false;
            return Objects.equals(barrierId, other.barrierId)
                    && Objects.equals(campaignDifficultyId, other.campaignDifficultyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(barrierId, campaignDifficultyId);
        }
    }
}
