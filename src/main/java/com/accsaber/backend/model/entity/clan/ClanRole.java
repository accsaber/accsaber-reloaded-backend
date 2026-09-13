package com.accsaber.backend.model.entity.clan;

public enum ClanRole {
    member,
    officer,
    commander,
    founder;

    public boolean isAtLeast(ClanRole required) {
        return compareTo(required) >= 0;
    }
}
