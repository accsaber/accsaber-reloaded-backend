package com.accsaber.backend.model.entity.clan;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.accsaber.backend.model.entity.user.User;

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
@Table(name = "clan_alliances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanAlliance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clan_a_id", nullable = false)
    private Clan clanA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clan_b_id", nullable = false)
    private Clan clanB;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by_clan_id", nullable = false)
    private Clan proposedByClan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by_user_id", nullable = false)
    private User proposedByUser;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ClanAllianceStatus status = ClanAllianceStatus.pending;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ended_by_user_id")
    private User endedByUser;
}
