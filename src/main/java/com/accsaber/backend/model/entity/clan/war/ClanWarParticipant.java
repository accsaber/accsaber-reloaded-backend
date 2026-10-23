package com.accsaber.backend.model.entity.clan.war;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clan_war_participants")
@IdClass(ClanWarParticipant.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWarParticipant {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "war_id", nullable = false)
    private ClanWar war;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ClanWarTargeting targeting = ClanWarTargeting.follow;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_kind", nullable = false)
    @Builder.Default
    private ClanWarOrder order = ClanWarOrder.auto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordered_target_user_id")
    private User orderedTarget;

    @Column(name = "standing_weight", nullable = false)
    private double standingWeight;

    @Column(nullable = false)
    private double guard;

    @Column(name = "guard_cycle", nullable = false)
    private int guardCycle;

    @Column(name = "breaks_suffered", nullable = false)
    private int breaksSuffered;

    @Column(name = "broken_at")
    private Instant brokenAt;

    @Column(nullable = false)
    private double contribution;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "xp_awarded")
    private Double xpAwarded;

    @Column(name = "rewarded_at")
    private Instant rewardedAt;

    public User aim() {
        return switch (targeting) {
            case manual -> target;
            case auto -> null;
            case follow -> orderedTarget;
        };
    }

    public boolean halted() {
        return targeting == ClanWarTargeting.follow && order == ClanWarOrder.halt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID war;
        private Long user;
    }
}
