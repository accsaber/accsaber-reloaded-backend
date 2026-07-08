package com.accsaber.backend.model.entity.item;

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
@Table(name = "crate_unusual_effects")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrateUnusualEffect {

    @EmbeddedId
    private CrateUnusualEffectId id;

    @MapsId("crateItemId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crate_item_id", nullable = false)
    private Item crateItem;

    @MapsId("effectId")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "effect_id", nullable = false)
    private UnusualEffect effect;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Embeddable
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrateUnusualEffectId implements Serializable {

        @Column(name = "crate_item_id")
        private UUID crateItemId;

        @Column(name = "effect_id")
        private UUID effectId;

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CrateUnusualEffectId other))
                return false;
            return Objects.equals(crateItemId, other.crateItemId)
                    && Objects.equals(effectId, other.effectId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(crateItemId, effectId);
        }
    }
}
