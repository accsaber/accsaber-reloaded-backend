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
@Table(name = "clan_level_capacities")
@IdClass(ClanLevelCapacity.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanLevelCapacity {

    @Id
    @Column(nullable = false)
    private int level;

    @Id
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ClanCapacity capacity;

    @Column(nullable = false)
    private int amount;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private int level;
        private ClanCapacity capacity;
    }
}
