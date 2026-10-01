package com.accsaber.backend.model.entity.clan.war;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "clan_war_hits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWarHit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "war_id", nullable = false)
    private ClanWar war;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attacker_user_id", nullable = false)
    private User attacker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "victim_user_id", nullable = false)
    private User victim;

    @Column(name = "victim_cycle", nullable = false)
    private int victimCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_difficulty_id", nullable = false)
    private MapDifficulty mapDifficulty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attacker_score_id", nullable = false)
    private Score attackerScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "victim_score_id")
    private Score victimScore;

    @Column(nullable = false)
    private double damage;

    @Column(name = "guard_after", nullable = false)
    private double guardAfter;

    @Column(nullable = false)
    private boolean broke;

    @Column(name = "standing_moved", nullable = false)
    private double standingMoved;

    @Column(name = "xp_awarded")
    private Double xpAwarded;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
