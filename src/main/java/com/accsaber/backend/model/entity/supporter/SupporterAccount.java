package com.accsaber.backend.model.entity.supporter;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "supporter_accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupporterAccount {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "balance_cents", nullable = false)
    @Builder.Default
    private Integer balanceCents = 0;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_tier", referencedColumnName = "tier_key")
    private SupporterTier currentTier;

    @Column(name = "tier_started_at")
    private Instant tierStartedAt;

    @Column(name = "last_debit_at")
    private Instant lastDebitAt;

    @Column(name = "lifetime_supported_cents", nullable = false)
    @Builder.Default
    private Long lifetimeSupportedCents = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
