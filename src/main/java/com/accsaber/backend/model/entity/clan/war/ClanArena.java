package com.accsaber.backend.model.entity.clan.war;

public enum ClanArena {
    mixed,
    random,
    category_turf,
    complexity_turf,
    modern_random;

    public boolean randomPool() {
        return this == random || this == modern_random;
    }
}
