package com.accsaber.backend.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.accsaber.backend.model.entity.staff.StaffUser;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StaffUserDetails implements UserDetails {

    private final StaffUser staffUser;

    public StaffUser getStaffUser() {
        return staffUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + staffUser.getRole().name()));
    }

    @Override
    public String getPassword() {
        return staffUser.getPassword();
    }

    @Override
    public String getUsername() {
        return staffUser.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return staffUser.isActive();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
