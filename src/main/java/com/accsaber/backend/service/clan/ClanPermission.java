package com.accsaber.backend.service.clan;

import com.accsaber.backend.model.entity.clan.ClanRole;

public enum ClanPermission {
    READ_AUDIT(ClanRole.member),
    CHAT(ClanRole.member),
    INVITE(ClanRole.officer),
    SUBMIT_PICKS(ClanRole.officer),
    RESOLVE_REQUESTS(ClanRole.officer),
    KICK(ClanRole.officer),
    PROMOTE_OFFICER(ClanRole.commander),
    MANAGE_RIVALS(ClanRole.commander),
    DECLARE_WAR(ClanRole.commander),
    LEND(ClanRole.commander),
    PROMOTE_COMMANDER(ClanRole.founder),
    MANAGE_ALLIANCES(ClanRole.founder),
    CUSTOMIZE(ClanRole.founder),
    TRANSFER(ClanRole.founder),
    DISBAND(ClanRole.founder);

    private final ClanRole minimum;

    ClanPermission(ClanRole minimum) {
        this.minimum = minimum;
    }

    public ClanRole minimum() {
        return minimum;
    }
}
