package com.accsaber.backend.security;

import java.io.IOException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static final Map<String, List<StaffRole>> SUBDOMAIN_ROLES = Map.of(
            "ranking", List.of(StaffRole.RANKING_HEAD, StaffRole.RANKING),
            "creatives", List.of(StaffRole.CREATIVE));

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

        UserDetails userDetails = loadUserDetails(token, playerRolesFor(
                request.getHeader("X-AccSaber-Realm"),
                request.getHeader("Origin"),
                request.getHeader("Referer")));
        if (userDetails != null) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null,
                    userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }

    private UserDetails loadUserDetails(String token, List<StaffRole> surfacedRoles) {
        String type = jwtService.extractTokenType(token);
        if (JwtService.TYPE_PLAYER.equals(type)) {
            Long userId = jwtService.extractPlayerId(token);
            String scope = jwtService.extractPlayerScope(token);
            return userRepository.findByIdAndActiveTrue(userId)
                    .map(user -> {
                        StaffUser staff = surfaceStaffRole(userId, surfacedRoles);
                        return new PlayerUserDetails(
                                user,
                                staff != null ? staff.getId() : null,
                                staff != null ? staff.getRole() : null,
                                scope);
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

    private StaffUser surfaceStaffRole(Long userId, List<StaffRole> surfacedRoles) {
        if (surfacedRoles.isEmpty()) {
            return null;
        }
        return staffUserRepository
                .findByUserIdAndRoleInAndStatusAndActiveTrue(userId, surfacedRoles, StaffUserStatus.ACCEPTED)
                .stream()
                .min(Comparator.comparingInt((StaffUser s) -> surfacedRoles.indexOf(s.getRole())))
                .orElse(null);
    }

    static List<StaffRole> playerRolesFor(String realmHeader, String origin, String referer) {
        if (realmHeader != null) {
            List<StaffRole> byName = SUBDOMAIN_ROLES.get(realmHeader.trim().toLowerCase(Locale.ROOT));
            if (byName != null) {
                return byName;
            }
        }
        List<StaffRole> byOrigin = rolesForUrlHeader(origin);
        return byOrigin.isEmpty() ? rolesForUrlHeader(referer) : byOrigin;
    }

    private static List<StaffRole> rolesForUrlHeader(String url) {
        if (url == null) {
            return List.of();
        }
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        if (host == null || !isAccsaberHost(host)) {
            return List.of();
        }
        int dot = host.indexOf('.');
        String subdomain = dot > 0 ? host.substring(0, dot) : "";
        return SUBDOMAIN_ROLES.getOrDefault(subdomain, List.of());
    }

    private static boolean isAccsaberHost(String host) {
        return host.equals("accsaberreloaded.com") || host.endsWith(".accsaberreloaded.com")
                || host.equals("localhost") || host.endsWith(".localhost");
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
