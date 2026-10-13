package com.accsaber.backend.model.entity.clan.war;

public enum ClanWarTeam {
    attack,
    defend;

    public ClanWarTeam opponent() {
        return this == attack ? defend : attack;
    }
}
