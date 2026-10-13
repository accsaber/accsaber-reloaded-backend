package com.accsaber.backend.service.clan;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanAlliance;
import com.accsaber.backend.model.entity.clan.ClanJoinDirection;
import com.accsaber.backend.model.entity.clan.ClanJoinRequest;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarAid;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidDirection;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.service.notification.NotificationService;

@ExtendWith(MockitoExtension.class)
class ClanNotifierTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private ClanMemberRepository memberRepository;

    @InjectMocks
    private ClanNotifier notifier;

    private final Clan owls = Clan.builder().id(new UUID(1L, 1L)).name("Night Owls").tag("NOW").slug("night-owls")
            .build();
    private final Clan lapiz = Clan.builder().id(new UUID(2L, 1L)).name("El Lapiz").tag("LPZ").slug("el-lapiz").build();
    private final User founder = User.builder().id(1L).build();
    private final User player = User.builder().id(2L).build();
    private final Clan fish = Clan.builder().id(new UUID(3L, 1L)).name("Fish Nation").tag("FISH").slug("fish")
            .build();
    private static final List<ClanRole> COMMANDERS = List.of(ClanRole.commander, ClanRole.founder);

    @Test
    void anInviteReachesTheInvitedPlayerFromWhoeverSentIt() {
        notifier.invited(ClanJoinRequest.builder().clan(owls).user(player).createdBy(founder)
                .direction(ClanJoinDirection.invite).build());

        verify(notificationService).notify(2L, NotificationType.clan_membership, 1L,
                "[NOW] Night Owls invited you to join", "/clans/night-owls");
    }

    @Test
    void aJoinRequestReachesEveryoneWhoCanResolveIt() {
        User asker = User.builder().id(3L).name("Lapiz").build();
        when(memberRepository.findOpenUserIdsByRoles(owls.getId(),
                List.of(ClanRole.officer, ClanRole.commander, ClanRole.founder))).thenReturn(List.of(1L, 4L));

        notifier.requested(ClanJoinRequest.builder().clan(owls).user(asker).createdBy(asker)
                .direction(ClanJoinDirection.request).build());

        verify(notificationService).notifyAll(List.of(1L, 4L), NotificationType.clan_membership, 3L,
                "Lapiz asked to join [NOW] Night Owls", "/clans/night-owls");
    }

    @Test
    void anAllianceChangeReachesTheOtherClansFounder() {
        ClanAlliance alliance = ClanAlliance.builder().clanA(owls).clanB(lapiz).proposedByClan(lapiz).build();
        when(memberRepository.findOpenFounders(List.of(lapiz.getId())))
                .thenReturn(List.of(ClanMember.builder().clan(lapiz).user(player).role(ClanRole.founder).build()));

        notifier.allianceChanged(alliance, lapiz, founder, "ended");

        verify(notificationService).notify(2L, NotificationType.clan_alliance, 1L,
                "[NOW] Night Owls ended your alliance", "/clans/el-lapiz");
    }

    @Test
    void aDeclarationReachesEveryDefenderButNoAttacker() {
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(owls).defenderClan(lapiz)
                .declaredBy(founder).build();
        when(memberRepository.findOpenUserIds(lapiz.getId())).thenReturn(List.of(2L, 3L));

        notifier.warDeclared(war);

        verify(notificationService).notifyAll(List.of(2L, 3L), NotificationType.clan_war, 1L,
                "[NOW] Night Owls declared war on your clan", "/clans/wars/" + war.getId());
        verify(memberRepository, never()).findOpenUserIds(owls.getId());
    }

    @Test
    void anEndedWarTellsEveryClanStillFightingWhoTheyFoughtAndHowItWent() {
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(owls).defenderClan(lapiz)
                .outcome(ClanWarOutcome.attacker_won).build();
        ClanWarSide left = ClanWarSide.builder().clan(Clan.builder().id(UUID.randomUUID()).build())
                .team(ClanWarTeam.defend).leftAt(Instant.now()).build();
        when(memberRepository.findOpenUserIds(owls.getId())).thenReturn(List.of(1L));
        when(memberRepository.findOpenUserIds(lapiz.getId())).thenReturn(List.of(2L));
        when(memberRepository.findOpenUserIds(fish.getId())).thenReturn(List.of(3L));

        notifier.warEnded(war, List.of(ClanWarSide.builder().clan(owls).team(ClanWarTeam.attack).build(),
                ClanWarSide.builder().clan(lapiz).team(ClanWarTeam.defend).build(),
                ClanWarSide.builder().clan(fish).team(ClanWarTeam.defend).build(), left));

        String link = "/clans/wars/" + war.getId();
        verify(notificationService).notifyAll(List.of(1L), NotificationType.clan_war, null,
                "Your war against [LPZ] El Lapiz is over: attacker won", link);
        verify(notificationService).notifyAll(List.of(2L), NotificationType.clan_war, null,
                "Your war against [NOW] Night Owls is over: attacker won", link);
        verify(notificationService).notifyAll(List.of(3L), NotificationType.clan_war, null,
                "Your war against [NOW] Night Owls is over: attacker won", link);
        verify(memberRepository, never()).findOpenUserIds(left.getClan().getId());
    }

    @Test
    void aStaffDisbandIsAServerNoticeCarryingTheReason() {
        notifier.disbandedByStaff(owls, List.of(1L, 2L), "offensive name");

        verify(notificationService).notifyAll(List.of(1L, 2L), NotificationType.server, null,
                "[NOW] Night Owls was disbanded by staff: offensive name", "/clans");
    }

    @Test
    void aRequestForHelpReachesTheAskedClansCommanders() {
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(owls).defenderClan(lapiz).build();
        when(memberRepository.findOpenUserIdsByRoles(fish.getId(), COMMANDERS)).thenReturn(List.of(5L));

        notifier.aidRequested(ClanWarAid.builder().war(war).clan(fish).team(ClanWarTeam.defend)
                .direction(ClanWarAidDirection.request).requestedBy(player).build());

        verify(notificationService).notifyAll(List.of(5L), NotificationType.clan_war, 2L,
                "[LPZ] El Lapiz asked your clan to help in its war against [NOW] Night Owls",
                "/clans/wars/" + war.getId());
    }

    @Test
    void anOfferReachesTheLeadOfTheTeamItWantsToJoin() {
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(owls).defenderClan(lapiz).build();
        when(memberRepository.findOpenUserIdsByRoles(lapiz.getId(), COMMANDERS)).thenReturn(List.of(2L));

        notifier.aidOffered(ClanWarAid.builder().war(war).clan(fish).team(ClanWarTeam.defend)
                .direction(ClanWarAidDirection.offer).requestedBy(founder).build());

        verify(notificationService).notifyAll(List.of(2L), NotificationType.clan_war, 1L,
                "[FISH] Fish Nation offered to fight on your side against [NOW] Night Owls",
                "/clans/wars/" + war.getId());
    }

    @Test
    void anAnswerGoesBackToTheClanThatAsked() {
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(owls).defenderClan(lapiz).build();
        when(memberRepository.findOpenUserIdsByRoles(lapiz.getId(), COMMANDERS)).thenReturn(List.of(2L));

        notifier.aidAnswered(ClanWarAid.builder().war(war).clan(fish).team(ClanWarTeam.defend)
                .direction(ClanWarAidDirection.request).status(ClanWarAidStatus.declined).requestedBy(player)
                .build(), founder);

        verify(notificationService).notifyAll(List.of(2L), NotificationType.clan_war, 1L,
                "[FISH] Fish Nation declined the call to fight against [NOW] Night Owls",
                "/clans/wars/" + war.getId());
    }

    @Test
    void aCancelledOfferTellsTheLeadItWasOfferedTo() {
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(owls).defenderClan(lapiz).build();
        when(memberRepository.findOpenUserIdsByRoles(lapiz.getId(), COMMANDERS)).thenReturn(List.of(2L));

        notifier.aidAnswered(ClanWarAid.builder().war(war).clan(fish).team(ClanWarTeam.defend)
                .direction(ClanWarAidDirection.offer).status(ClanWarAidStatus.cancelled).requestedBy(founder)
                .build(), founder);

        verify(notificationService).notifyAll(List.of(2L), NotificationType.clan_war, 1L,
                "[FISH] Fish Nation cancelled the call to fight against [NOW] Night Owls",
                "/clans/wars/" + war.getId());
    }
}
