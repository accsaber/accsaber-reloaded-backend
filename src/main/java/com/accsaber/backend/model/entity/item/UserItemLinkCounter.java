package com.accsaber.backend.model.entity.item;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_item_link_counters")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserItemLinkCounter {

    @EmbeddedId
    private CounterId id;

    @MapsId("userItemLinkId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_item_link_id", nullable = false)
    private UserItemLink userItemLink;

    @Column(nullable = false)
    @Builder.Default
    private Long value = 0L;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Embeddable
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CounterId implements Serializable {

        @Column(name = "user_item_link_id")
        private UUID userItemLinkId;

        @Column(name = "stat_key")
        private String statKey;

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CounterId other))
                return false;
            return Objects.equals(userItemLinkId, other.userItemLinkId)
                    && Objects.equals(statKey, other.statKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userItemLinkId, statKey);
        }
    }
}
