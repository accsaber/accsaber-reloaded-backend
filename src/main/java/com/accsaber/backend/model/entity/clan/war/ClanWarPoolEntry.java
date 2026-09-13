package com.accsaber.backend.model.entity.clan.war;

import java.io.Serializable;
import java.util.UUID;

import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.map.MapDifficulty;

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
@Table(name = "clan_war_pool")
@IdClass(ClanWarPoolEntry.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWarPoolEntry {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "war_id", nullable = false)
    private ClanWar war;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_difficulty_id", nullable = false)
    private MapDifficulty mapDifficulty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "picked_by_clan_id")
    private Clan pickedByClan;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ClanWarPoolSource source;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID war;
        private UUID mapDifficulty;
    }
}
