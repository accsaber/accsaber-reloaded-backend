package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class ClanAccessServiceTest {

    private static final UUID CLAN = UUID.randomUUID();

    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;

    @InjectMocks
    private ClanAccessService accessService;

    @Nested
    class Player {

        @Test
        void resolvesThePrimaryAccount() {
            User primary = User.builder().id(2L).build();
            when(duplicateUserService.resolvePrimaryUserId(1L)).thenReturn(2L);
            when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(primary));

            assertThat(accessService.player(1L)).isSameAs(primary);
        }

        @Test
        void bannedPlayersAreTurnedAway() {
            when(duplicateUserService.resolvePrimaryUserId(1L)).thenReturn(1L);
            when(userRepository.findByIdAndActiveTrue(1L))
                    .thenReturn(Optional.of(User.builder().id(1L).banned(true).build()));

            assertThatThrownBy(() -> accessService.player(1L)).isInstanceOf(ForbiddenException.class);
        }

        @Test
        void unknownPlayersAreNotFound() {
            when(duplicateUserService.resolvePrimaryUserId(1L)).thenReturn(1L);

            assertThatThrownBy(() -> accessService.player(1L)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Require {

        @Test
        void nonMembersAreForbidden() {
            assertThatThrownBy(() -> accessService.require(CLAN, 1L, ClanPermission.READ_AUDIT))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void aRankBelowThePermissionIsForbidden() {
            member(ClanRole.officer);

            assertThatThrownBy(() -> accessService.require(CLAN, 1L, ClanPermission.PROMOTE_OFFICER))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void aRankAtOrAboveThePermissionPasses() {
            ClanMember commander = member(ClanRole.commander);

            assertThat(accessService.require(CLAN, 1L, ClanPermission.KICK)).isSameAs(commander);
            assertThat(accessService.require(CLAN, 1L, ClanPermission.PROMOTE_OFFICER)).isSameAs(commander);
        }

        private ClanMember member(ClanRole role) {
            ClanMember member = ClanMember.builder().role(role).build();
            when(memberRepository.findOpenByClanIdAndUserId(CLAN, 1L)).thenReturn(Optional.of(member));
            return member;
        }
    }
}
