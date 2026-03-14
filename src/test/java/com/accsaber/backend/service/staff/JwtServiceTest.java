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
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenTtl", 3600L);
    }

    @Test
    void generateAccessToken_containsExpectedClaims() {
        StaffUser staffUser = buildStaffUser(StaffRole.ADMIN);
        String token = jwtService.generateAccessToken(staffUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractStaffId(token)).isEqualTo(staffUser.getId());
        assertThat(jwtService.extractRole(token)).isEqualTo(StaffRole.ADMIN);
    }

    @Test
    void validateToken_validToken_doesNotThrow() {
        StaffUser staffUser = buildStaffUser(StaffRole.RANKING);
        String token = jwtService.generateAccessToken(staffUser);

        assertThatCode(() -> jwtService.validateToken(token)).doesNotThrowAnyException();
    }

    @Test
    void validateToken_expiredToken_throwsJwtException() {
        JwtService shortLivedService = new JwtService();
        ReflectionTestUtils.setField(shortLivedService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(shortLivedService, "accessTokenTtl", -1L);

        String token = shortLivedService.generateAccessToken(buildStaffUser(StaffRole.RANKING));

        assertThatThrownBy(() -> jwtService.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateToken_tamperedToken_throwsJwtException() {
        String tampered = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.invalid-signature";

        assertThatThrownBy(() -> jwtService.validateToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractStaffId_returnsCorrectUuid() {
        StaffUser staffUser = buildStaffUser(StaffRole.RANKING_HEAD);
        String token = jwtService.generateAccessToken(staffUser);

        UUID extracted = jwtService.extractStaffId(token);

        assertThat(extracted).isEqualTo(staffUser.getId());
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
