package com.accsaber.backend.security;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.staff.JwtService;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<StaffRole> PLAYER_LINKED_ROLES = List.of(StaffRole.RANKING, StaffRole.RANKING_HEAD);
    private static final Comparator<StaffUser> HIGHEST_ROLE_FIRST = Comparator
            .comparingInt((StaffUser s) -> PLAYER_LINKED_ROLES.indexOf(s.getRole()))
            .reversed();

    private final JwtService jwtService;
    private final StaffUserRepository staffUserRepository;
    private final UserRepository userRepository;

    @Value("${accsaber.service.api-key}")
    private String serviceApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String serviceToken = request.getHeader("X-Service-Token");
        if (serviceToken != null && serviceToken.equals(serviceApiKey)) {
            SecurityContextHolder.getContext().setAuthentication(new ServiceAuthentication());
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            jwtService.validateToken(token);
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        UserDetails userDetails = loadUserDetails(token);
        if (userDetails != null) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null,
                    userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }

    private UserDetails loadUserDetails(String token) {
        String type = jwtService.extractTokenType(token);
        if (JwtService.TYPE_PLAYER.equals(type)) {
            Long userId = jwtService.extractPlayerId(token);
            return userRepository.findByIdAndActiveTrue(userId)
                    .map(user -> {
                        StaffUser staff = staffUserRepository
                                .findByUserIdAndRoleInAndStatusAndActiveTrue(
                                        userId, PLAYER_LINKED_ROLES, StaffUserStatus.ACCEPTED)
                                .stream()
                                .min(HIGHEST_ROLE_FIRST)
                                .orElse(null);
                        return new PlayerUserDetails(
                                user,
                                staff != null ? staff.getId() : null,
                                staff != null ? staff.getRole() : null);
                    })
                    .orElse(null);
        }
        if (JwtService.TYPE_STAFF.equals(type)) {
            return staffUserRepository.findByIdAndActiveTrue(jwtService.extractStaffId(token))
                    .map(StaffUserDetails::new)
                    .orElse(null);
        }
        log.warn("Rejecting JWT with unknown token type: {}", type);
        return null;
    }

    static class ServiceAuthentication extends AbstractAuthenticationToken {

        ServiceAuthentication() {
            super(List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return "service";
        }
    }
}
