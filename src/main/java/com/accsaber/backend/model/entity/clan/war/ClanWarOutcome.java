package com.accsaber.backend.model.entity.clan.war;

public enum ClanWarOutcome {
    attacker_won,
    defender_won,
    drawn,
    retreated,
    forfeited,
    season_ended;

    public static ClanWarOutcome wonBy(ClanWarTeam team) {
        return team == ClanWarTeam.attack ? attacker_won : defender_won;
    }
}
