package com.accsaber.backend.service.clan;

import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.user.User;

public record ChatNotice(ChatEvent event, User actor, User subjectUser, Clan subjectClan, ClanWar war) {

    public static ChatNotice ofPlayer(ChatEvent event, User actor, User subjectUser) {
        return new ChatNotice(event, actor, subjectUser, null, null);
    }

    public static ChatNotice ofClan(ChatEvent event, User actor, Clan subjectClan) {
        return new ChatNotice(event, actor, null, subjectClan, null);
    }

    public static ChatNotice ofWar(ChatEvent event, User actor, Clan subjectClan, ClanWar war) {
        return new ChatNotice(event, actor, null, subjectClan, war);
    }
}
