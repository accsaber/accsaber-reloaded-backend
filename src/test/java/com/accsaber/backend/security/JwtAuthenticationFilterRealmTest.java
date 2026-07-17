package com.accsaber.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.entity.staff.StaffRole;

class JwtAuthenticationFilterRealmTest {

    @Test
    void explicitRealmHeaderTakesPrecedence() {
        assertThat(JwtAuthenticationFilter.playerRolesFor("creatives", "https://ranking.accsaber.com", null))
                .containsExactly(StaffRole.CREATIVE);
    }

    @Test
    void realmHeaderIsCaseAndWhitespaceInsensitive() {
        assertThat(JwtAuthenticationFilter.playerRolesFor("  Creatives  ", null, null))
                .containsExactly(StaffRole.CREATIVE);
    }

    @Test
    void unknownRealmHeaderFallsThroughToOrigin() {
        assertThat(JwtAuthenticationFilter.playerRolesFor("bogus", "https://ranking.accsaber.com", null))
                .containsExactly(StaffRole.RANKING_HEAD, StaffRole.RANKING);
    }

    @Test
    void creativesOriginSurfacesOnlyCreative() {
        assertThat(JwtAuthenticationFilter.playerRolesFor(null, "https://creatives.accsaber.com", null))
                .containsExactly(StaffRole.CREATIVE);
    }

    @Test
    void refererIsUsedWhenOriginAbsent() {
        assertThat(JwtAuthenticationFilter.playerRolesFor(null, null, "http://creatives.localhost:5173/live-preview"))
                .containsExactly(StaffRole.CREATIVE);
    }

    @Test
    void originWinsOverReferer() {
        assertThat(JwtAuthenticationFilter.playerRolesFor(
                null, "https://ranking.accsaber.com", "https://creatives.accsaber.com"))
                .containsExactly(StaffRole.RANKING_HEAD, StaffRole.RANKING);
    }

    @Test
    void adminOriginSurfacesNothing() {
        assertThat(JwtAuthenticationFilter.playerRolesFor(null, "https://admin.accsaber.com", null))
                .isEmpty();
    }

    @Test
    void apexAndWwwSurfaceNothing() {
        assertThat(JwtAuthenticationFilter.playerRolesFor(null, "https://accsaber.com", null)).isEmpty();
        assertThat(JwtAuthenticationFilter.playerRolesFor(null, "https://www.accsaber.com", null)).isEmpty();
    }

    @Test
    void foreignHostSurfacesNothing() {
        assertThat(JwtAuthenticationFilter.playerRolesFor(null, "https://ranking.evil.com", null)).isEmpty();
        assertThat(JwtAuthenticationFilter.playerRolesFor(null, null, "https://creatives.evil.com/x")).isEmpty();
    }

    @Test
    void noSignalsSurfaceNothing() {
        assertThat(JwtAuthenticationFilter.playerRolesFor(null, null, null)).isEmpty();
        assertThat(JwtAuthenticationFilter.playerRolesFor(null, "not a uri", null)).isEmpty();
    }
}
