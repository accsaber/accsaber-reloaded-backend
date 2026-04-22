package com.accsaber.backend.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.user.User;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PlayerUserDetails implements UserDetails {

    private final User user;

    @Getter
    private final UUID staffId;

    @Getter
    private final StaffRole staffRole;

    public PlayerUserDetails(User user) {
        this(user, null, null);
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_PLAYER"));
        if (staffRole != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + staffRole.name()));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return user.getName();
    }

    @Override
    public boolean isEnabled() {
        return user.isActive() && !user.isBanned();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isBanned();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
