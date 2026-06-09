package com.accsaber.backend.model.entity.campaign;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.accsaber.backend.model.entity.item.Item;

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
@Table(name = "campaign_difficulty_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDifficultyItem {

    @EmbeddedId
    private CampaignDifficultyItemId id;

    @MapsId("campaignDifficultyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_difficulty_id", nullable = false)
    private CampaignDifficulty campaignDifficulty;

    @MapsId("itemId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Embeddable
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignDifficultyItemId implements Serializable {

        @Column(name = "campaign_difficulty_id")
        private UUID campaignDifficultyId;

        @Column(name = "item_id")
        private UUID itemId;

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CampaignDifficultyItemId other))
                return false;
            return Objects.equals(campaignDifficultyId, other.campaignDifficultyId)
                    && Objects.equals(itemId, other.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(campaignDifficultyId, itemId);
        }
    }
}
