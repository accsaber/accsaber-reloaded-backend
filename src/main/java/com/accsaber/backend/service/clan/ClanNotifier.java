package com.accsaber.backend.service.clan;

import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanAlliance;
import com.accsaber.backend.model.entity.clan.ClanJoinRequest;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarAid;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidDirection;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClanNotifier {

    private final NotificationService notificationService;
    private final ClanMemberRepository memberRepository;

    public void invited(ClanJoinRequest invite) {
        notificationService.notify(invite.getUser().getId(), NotificationType.clan_membership,
                invite.getCreatedBy().getId(), label(invite.getClan()) + " invited you to join", clanLink(invite.getClan()));
    }

    public void requested(ClanJoinRequest request) {
        notificationService.notifyAll(memberRepository.findOpenUserIdsByRoles(request.getClan().getId(),
                ClanPermission.RESOLVE_REQUESTS.roles()),
                NotificationType.clan_membership, request.getUser().getId(),
                request.getUser().getName() + " asked to join " + label(request.getClan()), clanLink(request.getClan()));
    }

    public void admitted(ClanJoinRequest request, User actor) {
        notificationService.notify(request.getUser().getId(), NotificationType.clan_membership, actor.getId(),
                "You joined " + label(request.getClan()), clanLink(request.getClan()));
    }

    public void kicked(Clan clan, User target, User actor) {
        notificationService.notify(target.getId(), NotificationType.clan_membership, actor.getId(),
                "You were kicked from " + label(clan), clanLink(clan));
    }

    public void crowned(Clan clan, User heir, User actor) {
        notificationService.notify(heir.getId(), NotificationType.clan_membership, actor == null ? null : actor.getId(),
                "You are now the founder of " + label(clan), clanLink(clan));
    }

    public void disbandedByStaff(Clan clan, List<Long> memberIds, String reason) {
        notificationService.notifyAll(memberIds, NotificationType.server, null,
                label(clan) + " was disbanded by staff: " + reason, "/clans");
    }

    public void allianceProposed(ClanAlliance alliance, Clan to) {
        founder(to, alliance.getProposedByUser(), label(alliance.getProposedByClan()) + " proposed an alliance");
    }

    public void allianceChanged(ClanAlliance alliance, Clan to, User actor, String verb) {
        Clan from = alliance.otherThan(to.getId());
        founder(to, actor, label(from) + " " + verb + " your alliance");
    }

    public void warDeclared(ClanWar war) {
        notificationService.notifyAll(memberRepository.findOpenUserIds(war.getDefenderClan().getId()),
                NotificationType.clan_war, war.getDeclaredBy().getId(),
                label(war.getAttackerClan()) + " declared war on your clan", warLink(war));
    }

    public void warStarted(ClanWar war, List<ClanWarSide> sides) {
        everySide(war, sides, side -> "Your war against " + label(war.lead(side.getTeam().opponent()))
                + " has started");
    }

    public void retreating(ClanWar war, List<ClanWarSide> sides) {
        everySide(war, sides.stream().filter(side -> side.getTeam() == ClanWarTeam.defend).toList(),
                side -> label(war.getAttackerClan()) + " is retreating. Strike back before the war ends");
    }

    public void warEnded(ClanWar war, List<ClanWarSide> sides) {
        everySide(war, sides, side -> "Your war against " + label(war.lead(side.getTeam().opponent()))
                + " is over: " + war.getOutcome().name().replace('_', ' '));
    }

    public void aidRequested(ClanWarAid aid) {
        ClanWar war = aid.getWar();
        commanders(aid.getClan(), aid.getRequestedBy(), label(war.lead(aid.getTeam()))
                + " asked your clan to help in its war against " + label(war.lead(aid.getTeam().opponent())), war);
    }

    public void aidOffered(ClanWarAid aid) {
        ClanWar war = aid.getWar();
        commanders(war.lead(aid.getTeam()), aid.getRequestedBy(), label(aid.getClan())
                + " offered to fight on your side against " + label(war.lead(aid.getTeam().opponent())), war);
    }

    public void aidAnswered(ClanWarAid aid, User actor) {
        ClanWar war = aid.getWar();
        boolean fromLead = aid.getDirection() == ClanWarAidDirection.request;
        boolean byInitiator = aid.getStatus() == ClanWarAidStatus.cancelled;
        Clan to = fromLead != byInitiator ? war.lead(aid.getTeam()) : aid.getClan();
        Clan by = to == aid.getClan() ? war.lead(aid.getTeam()) : aid.getClan();
        commanders(to, actor, label(by) + " " + aid.getStatus().name() + " the call to fight against "
                + label(war.lead(aid.getTeam().opponent())), war);
    }

    public void allyLeft(ClanWar war, ClanWarSide left) {
        notificationService.notifyAll(memberRepository.findOpenUserIds(war.lead(left.getTeam()).getId()),
                NotificationType.clan_war, null, label(left.getClan()) + " left your war against "
                        + label(war.lead(left.getTeam().opponent())), warLink(war));
    }

    private void founder(Clan clan, User actor, String title) {
        memberRepository.findOpenFounders(List.of(clan.getId())).forEach(founder -> notificationService.notify(
                founder.getUser().getId(), NotificationType.clan_alliance, actor == null ? null : actor.getId(), title,
                clanLink(clan)));
    }

    private void everySide(ClanWar war, List<ClanWarSide> sides, Function<ClanWarSide, String> title) {
        sides.stream().filter(side -> side.getLeftAt() == null).forEach(side -> notificationService.notifyAll(
                memberRepository.findOpenUserIds(side.getClan().getId()), NotificationType.clan_war, null,
                title.apply(side), warLink(war)));
    }

    private void commanders(Clan clan, User actor, String title, ClanWar war) {
        notificationService.notifyAll(memberRepository.findOpenUserIdsByRoles(clan.getId(),
                ClanPermission.COALITION.roles()),
                NotificationType.clan_war, actor == null ? null : actor.getId(), title, warLink(war));
    }

    private static String label(Clan clan) {
        return "[" + clan.getTag() + "] " + clan.getName();
    }

    private static String clanLink(Clan clan) {
        return "/clans/" + clan.getSlug();
    }

    private static String warLink(ClanWar war) {
        return "/clans/wars/" + war.getId();
    }
}
