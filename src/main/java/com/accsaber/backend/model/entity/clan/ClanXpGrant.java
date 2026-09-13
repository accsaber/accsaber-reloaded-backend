package com.accsaber.backend.model.entity.clan;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "clan_xp_grants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanXpGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ClanXpSource source;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "raw_amount", nullable = false)
    private double rawAmount;

    @Column(name = "roster_factor", nullable = false)
    private double rosterFactor;

    @Column(nullable = false)
    private double amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
