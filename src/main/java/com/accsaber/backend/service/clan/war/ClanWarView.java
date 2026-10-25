package com.accsaber.backend.service.clan.war;

public record ClanWarView(Long viewerId, Long as, Long focus) {

    public static ClanWarView of(Long viewerId) {
        return new ClanWarView(viewerId, null, null);
    }

    public Long perspective() {
        return as != null ? as : viewerId;
    }
}
