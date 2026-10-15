package com.accsaber.backend.service.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 3600L, 0L);
    }

    @Test
    void generateAccessToken_containsExpectedClaims() {
        StaffUser staffUser = buildStaffUser(StaffRole.ADMIN);
        String token = jwtService.generateAccessToken(staffUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.verify(token).staffId()).isEqualTo(staffUser.getId());
    }

    @Test
    void verify_staffTokenCarriesStaffType() {
        String token = jwtService.generateAccessToken(buildStaffUser(StaffRole.ADMIN));

        assertThat(jwtService.verify(token).type()).isEqualTo(JwtService.TYPE_STAFF);
    }

    @Test
    void verify_tokenWithoutTypIsNotTreatedAsStaff() {
        String token = Jwts.builder()
                .subject("discord")
                .claim("purpose", "oauth-state")
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET)))
                .compact();

        assertThat(jwtService.verify(token).type()).isNull();
    }

    @Test
    void verify_validToken_doesNotThrow() {
        StaffUser staffUser = buildStaffUser(StaffRole.RANKING);
        String token = jwtService.generateAccessToken(staffUser);

        assertThatCode(() -> jwtService.verify(token)).doesNotThrowAnyException();
    }

    @Test
    void verify_expiredToken_throwsJwtException() {
        JwtService shortLivedService = new JwtService(TEST_SECRET, -1L, 0L);

        String token = shortLivedService.generateAccessToken(buildStaffUser(StaffRole.RANKING));

        assertThatThrownBy(() -> jwtService.verify(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void verify_tamperedToken_throwsJwtException() {
        String tampered = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.invalid-signature";

        assertThatThrownBy(() -> jwtService.verify(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void generateRefreshToken_returnsNonNullUniqueStrings() {
        String first = jwtService.generateRefreshToken();
        String second = jwtService.generateRefreshToken();

        Set<String> tokens = new HashSet<>();
        tokens.add(first);
        tokens.add(second);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(tokens).hasSize(2);
    }

    private StaffUser buildStaffUser(StaffRole role) {
        return StaffUser.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .password("hashed")
                .role(role)
                .active(true)
                .build();
    }
}
