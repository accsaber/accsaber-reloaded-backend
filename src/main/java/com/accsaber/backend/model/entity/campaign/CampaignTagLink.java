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
@Table(name = "campaign_tag_links")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignTagLink {

    @EmbeddedId
    private CampaignTagLinkId id;

    @MapsId("campaignId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @MapsId("campaignTagId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_tag_id", nullable = false)
    private CampaignTag campaignTag;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Embeddable
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignTagLinkId implements Serializable {

        @Column(name = "campaign_id")
        private UUID campaignId;

        @Column(name = "campaign_tag_id")
        private UUID campaignTagId;

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CampaignTagLinkId other))
                return false;
            return Objects.equals(campaignId, other.campaignId)
                    && Objects.equals(campaignTagId, other.campaignTagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(campaignId, campaignTagId);
        }
    }
}
