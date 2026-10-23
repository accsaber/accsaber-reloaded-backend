package com.accsaber.backend.service.clan;

import java.util.Arrays;
import java.util.List;

import com.accsaber.backend.model.entity.clan.ClanRole;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
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
    COALITION(ClanRole.commander),
    ORDER_TARGETS(ClanRole.commander),
    PROMOTE_COMMANDER(ClanRole.founder),
    MANAGE_ALLIANCES(ClanRole.founder),
    MANAGE_PACIFISM(ClanRole.founder),
    CUSTOMIZE(ClanRole.founder),
    TRANSFER(ClanRole.founder),
    DISBAND(ClanRole.founder);

    private final ClanRole minimum;

    public List<ClanRole> roles() {
        return Arrays.stream(ClanRole.values()).filter(role -> role.isAtLeast(minimum)).toList();
    }
}
