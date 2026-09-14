package com.accsaber.backend.service.clan;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.accsaber.backend.model.entity.clan.war.ClanWarLoan;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
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

    @Test
    void anInviteReachesTheInvitedPlayerFromWhoeverSentIt() {
        notifier.invited(ClanJoinRequest.builder().clan(owls).user(player).createdBy(founder)
                .direction(ClanJoinDirection.invite).build());

        verify(notificationService).notify(2L, NotificationType.clan_membership, 1L,
                "[NOW] Night Owls invited you to join", "/clans/night-owls");
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
    void anEndedWarTellsEachSideWhoTheyFoughtAndHowItWent() {
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(owls).defenderClan(lapiz)
                .outcome(ClanWarOutcome.attacker_won).build();
        when(memberRepository.findOpenUserIds(owls.getId())).thenReturn(List.of(1L));
        when(memberRepository.findOpenUserIds(lapiz.getId())).thenReturn(List.of(2L));

        notifier.warEnded(war);

        String link = "/clans/wars/" + war.getId();
        verify(notificationService).notifyAll(List.of(1L), NotificationType.clan_war, null,
                "Your war against [LPZ] El Lapiz is over: attacker won", link);
        verify(notificationService).notifyAll(List.of(2L), NotificationType.clan_war, null,
                "Your war against [NOW] Night Owls is over: attacker won", link);
    }

    @Test
    void aStaffDisbandIsAServerNoticeCarryingTheReason() {
        notifier.disbandedByStaff(owls, List.of(1L, 2L), "offensive name");

        verify(notificationService).notifyAll(List.of(1L, 2L), NotificationType.server, null,
                "[NOW] Night Owls was disbanded by staff: offensive name", "/clans");
    }

    @Test
    void aLoanOfferReachesTheLentPlayer() {
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).build();

        notifier.loanOffered(ClanWarLoan.builder().war(war).clan(lapiz).lendingClan(owls).user(player)
                .offeredBy(founder).build());

        verify(notificationService).notify(2L, NotificationType.clan_war, 1L,
                "[NOW] Night Owls wants to lend you to [LPZ] El Lapiz for a war", "/clans/wars/" + war.getId());
    }
}
