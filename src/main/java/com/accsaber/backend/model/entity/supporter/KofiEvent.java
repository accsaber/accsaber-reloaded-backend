package com.accsaber.backend.model.entity.supporter;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.accsaber.backend.model.entity.user.User;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kofi_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KofiEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kofi_transaction_id", nullable = false, unique = true, length = 128)
    private String kofiTransactionId;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private KofiEventType type;

    @Column(length = 255)
    private String email;

    @Column(name = "from_name", length = 128)
    private String fromName;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "tier_name", length = 64)
    private String tierName;

    @Column(name = "is_subscription", nullable = false)
    @Builder.Default
    private boolean subscription = false;

    @Column(name = "is_first_subscription", nullable = false)
    @Builder.Default
    private boolean firstSubscription = false;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_user_id")
    private User claimedUser;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claim_source", length = 32)
    @Enumerated(EnumType.STRING)
    private KofiClaimSource claimSource;
}
