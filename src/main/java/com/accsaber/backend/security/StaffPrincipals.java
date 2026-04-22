package com.accsaber.backend.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.entity.staff.StaffRole;

public final class StaffPrincipals {

    private StaffPrincipals() {
    }

    public static UUID staffIdOf(Authentication auth) {
        Object p = principalOf(auth);
        if (p instanceof StaffUserDetails s) {
            return s.getStaffUser().getId();
        }
        if (p instanceof PlayerUserDetails player && player.getStaffId() != null) {
            return player.getStaffId();
        }
        throw new UnauthorizedException("Staff authentication required");
    }

    public static Long linkedUserIdOf(Authentication auth) {
        Object p = principalOf(auth);
        if (p instanceof StaffUserDetails s) {
            return s.getLinkedUserId();
        }
        if (p instanceof PlayerUserDetails player) {
            return player.getUserId();
        }
        return null;
    }

    public static StaffRole roleOf(Authentication auth) {
        Object p = principalOf(auth);
        if (p instanceof StaffUserDetails s) {
            return s.getStaffUser().getRole();
        }
        if (p instanceof PlayerUserDetails player && player.getStaffRole() != null) {
            return player.getStaffRole();
        }
        throw new UnauthorizedException("Staff role not present");
    }

    private static Object principalOf(Authentication auth) {
        if (auth == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return auth.getPrincipal();
    }
}
