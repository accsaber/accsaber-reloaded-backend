package com.accsaber.backend.model.entity.clan;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clan_level_war_modes")
@IdClass(ClanLevelWarMode.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanLevelWarMode {

    @Id
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ClanWarModeAxis axis;

    @Id
    @Column(nullable = false)
    private String mode;

    @Column(nullable = false)
    private int level;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private ClanWarModeAxis axis;
        private String mode;
    }
}
