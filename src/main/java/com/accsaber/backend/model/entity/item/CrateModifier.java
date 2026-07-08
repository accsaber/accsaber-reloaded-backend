package com.accsaber.backend.model.entity.item;

import java.io.Serializable;
import java.math.BigDecimal;
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
@Table(name = "crate_modifiers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrateModifier {

    @EmbeddedId
    private CrateModifierId id;

    @MapsId("crateItemId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crate_item_id", nullable = false)
    private Item crateItem;

    @MapsId("modifierId")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modifier_id", nullable = false)
    private ItemModifier modifier;

    @Column(name = "drop_chance", nullable = false)
    private BigDecimal dropChance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Embeddable
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrateModifierId implements Serializable {

        @Column(name = "crate_item_id")
        private UUID crateItemId;

        @Column(name = "modifier_id")
        private UUID modifierId;

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CrateModifierId other))
                return false;
            return Objects.equals(crateItemId, other.crateItemId)
                    && Objects.equals(modifierId, other.modifierId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(crateItemId, modifierId);
        }
    }
}
