package com.accsaber.backend.service.clan;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanAccessService {

    private final ClanMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;

    public User player(Long playerId) {
        Long userId = duplicateUserService.resolvePrimaryUserId(playerId);
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.isBanned()) {
            throw new ForbiddenException("Banned players cannot take part in clans");
        }
        return user;
    }

    public ClanMember require(UUID clanId, Long userId, ClanPermission permission) {
        ClanMember membership = memberRepository.findOpenByClanIdAndUserId(clanId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this clan"));
        if (!membership.getRole().isAtLeast(permission.minimum())) {
            throw new ForbiddenException("Your rank in this clan cannot do that");
        }
        return membership;
    }
}
