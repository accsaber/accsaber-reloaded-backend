package com.accsaber.backend.service.clan;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanAlliance;
import com.accsaber.backend.model.entity.clan.ClanJoinRequest;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoan;
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

    public void warStarted(ClanWar war) {
        bothSides(war, clan -> "Your war against " + label(opponent(war, clan)) + " has started");
    }

    public void warEnded(ClanWar war) {
        bothSides(war, clan -> "Your war against " + label(opponent(war, clan)) + " is over: "
                + war.getOutcome().name().replace('_', ' '));
    }

    public void loanOffered(ClanWarLoan loan) {
        notificationService.notify(loan.getUser().getId(), NotificationType.clan_war, loan.getOfferedBy().getId(),
                label(loan.getLendingClan()) + " wants to lend you to " + label(loan.getClan()) + " for a war",
                warLink(loan.getWar()));
    }

    private void founder(Clan clan, User actor, String title) {
        memberRepository.findOpenFounders(List.of(clan.getId())).forEach(founder -> notificationService.notify(
                founder.getUser().getId(), NotificationType.clan_alliance, actor == null ? null : actor.getId(), title,
                clanLink(clan)));
    }

    private void bothSides(ClanWar war, Function<Clan, String> title) {
        Stream.of(war.getAttackerClan(), war.getDefenderClan()).forEach(clan -> notificationService.notifyAll(
                memberRepository.findOpenUserIds(clan.getId()), NotificationType.clan_war, null, title.apply(clan),
                warLink(war)));
    }

    private static Clan opponent(ClanWar war, Clan clan) {
        return war.getAttackerClan().getId().equals(clan.getId()) ? war.getDefenderClan() : war.getAttackerClan();
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
