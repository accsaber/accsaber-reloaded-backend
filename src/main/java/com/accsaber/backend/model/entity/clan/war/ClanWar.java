package com.accsaber.backend.model.entity.clan.war;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.accsaber.backend.model.dto.ClanArenaSpec;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanSeason;
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
@Table(name = "clan_wars")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private ClanSeason season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attacker_clan_id", nullable = false)
    private Clan attackerClan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defender_clan_id", nullable = false)
    private Clan defenderClan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declared_by", nullable = false)
    private User declaredBy;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ClanArena arena;

    @Column(name = "arena_spec", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private ClanArenaSpec arenaSpec;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ClanRuleset ruleset;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ClanWarStatus status = ClanWarStatus.picking;

    @Enumerated(EnumType.STRING)
    private ClanWarOutcome outcome;

    @CreationTimestamp
    @Column(name = "declared_at", nullable = false, updatable = false)
    private Instant declaredAt;

    @Column(name = "picks_due_at")
    private Instant picksDueAt;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
