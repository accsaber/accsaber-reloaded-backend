package com.accsaber.backend.model.entity.clan;

import java.util.function.BiConsumer;
import java.util.function.Function;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ClanMedia {
    icon("clan-icons", Clan::getIconUrl, Clan::setIconUrl),
    banner("clan-banners", Clan::getBannerUrl, Clan::setBannerUrl);

    @Getter
    private final String subdir;
    private final Function<Clan, String> getter;
    private final BiConsumer<Clan, String> setter;

    public String urlOf(Clan clan) {
        return getter.apply(clan);
    }

    public void set(Clan clan, String url) {
        setter.accept(clan, url);
    }
}
