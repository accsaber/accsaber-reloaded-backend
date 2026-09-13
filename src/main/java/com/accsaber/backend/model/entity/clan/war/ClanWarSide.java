package com.accsaber.backend.model.entity.clan.war;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "clan_war_sides")
@IdClass(ClanWarSide.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWarSide {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "war_id", nullable = false)
    private ClanWar war;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_user_id")
    private User leadUser;

    @Column(nullable = false)
    private double stake;

    @Column(name = "stake_remaining", nullable = false)
    private double stakeRemaining;

    @Column(name = "standing_at_declare", nullable = false)
    private double standingAtDeclare;

    @Column(name = "picks_submitted_at")
    private Instant picksSubmittedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID war;
        private UUID clan;
    }
}
