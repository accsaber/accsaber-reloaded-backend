package com.accsaber.backend.model.entity.supporter;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "supporter_tiers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupporterTier {

    @Id
    @Column(name = "tier_key", length = 32)
    private String tierKey;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(name = "monthly_cost_cents", nullable = false)
    private Integer monthlyCostCents;

    @Column(name = "sort_order", nullable = false, unique = true)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
