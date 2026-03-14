package com.accsaber.backend.service.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.staff.LoginRequest;
import com.accsaber.backend.model.dto.request.staff.RefreshTokenRequest;
import com.accsaber.backend.model.dto.response.staff.AuthResponse;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;
import com.accsaber.backend.repository.staff.StaffUserRepository;

@ExtendWith(MockitoExtension.class)
class StaffAuthServiceTest {

    @Mock
    private StaffUserRepository staffUserRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private StaffAuthService staffAuthService;

    @Test
    void login_validCredentials_returnsAuthResponse() {
        ReflectionTestUtils.setField(staffAuthService, "accessTokenTtl", 3600L);
        ReflectionTestUtils.setField(staffAuthService, "refreshTokenTtl", 2592000L);

        StaffUser staffUser = buildStaffUser();
        LoginRequest request = new LoginRequest();
        request.setIdentifier("admin");
        request.setPassword("password");

        when(staffUserRepository.findByUsernameAndActiveTrue("admin")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(staffUser)).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
        when(staffUserRepository.save(any())).thenReturn(staffUser);

        AuthResponse response = staffAuthService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getRole()).isEqualTo(StaffRole.ADMIN);
    }

    @Test
    void login_unknownUsername_throwsUnauthorized() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("unknown");
        request.setPassword("password");

        when(staffUserRepository.findByUsernameAndActiveTrue("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffAuthService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        StaffUser staffUser = buildStaffUser();
        LoginRequest request = new LoginRequest();
        request.setIdentifier("admin");
        request.setPassword("wrong");

        when(staffUserRepository.findByUsernameAndActiveTrue("admin")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> staffAuthService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_inactiveUser_throwsUnauthorized() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("admin");
        request.setPassword("password");

        when(staffUserRepository.findByUsernameAndActiveTrue("admin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffAuthService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_validToken_returnsNewAccessToken() {
        ReflectionTestUtils.setField(staffAuthService, "accessTokenTtl", 3600L);
        ReflectionTestUtils.setField(staffAuthService, "refreshTokenTtl", 2592000L);

        StaffUser staffUser = buildStaffUser();
        staffUser.setRefreshToken("valid-refresh");
        staffUser.setTokenExpiresAt(Instant.now().plusSeconds(3600));

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh");

        when(staffUserRepository.findByRefreshToken("valid-refresh")).thenReturn(Optional.of(staffUser));
        when(jwtService.generateAccessToken(staffUser)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken()).thenReturn("new-refresh-token");
        when(staffUserRepository.save(any())).thenReturn(staffUser);

        AuthResponse response = staffAuthService.refresh(request);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
    }

    @Test
    void refresh_nonAcceptedStatus_throwsUnauthorized() {
        StaffUser staffUser = buildStaffUser();
        staffUser.setStatus(StaffUserStatus.DENIED);
        staffUser.setRefreshToken("valid-refresh");
        staffUser.setTokenExpiresAt(Instant.now().plusSeconds(3600));

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh");

        when(staffUserRepository.findByRefreshToken("valid-refresh")).thenReturn(Optional.of(staffUser));

        assertThatThrownBy(() -> staffAuthService.refresh(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        StaffUser staffUser = buildStaffUser();
        staffUser.setRefreshToken("expired-refresh");
        staffUser.setTokenExpiresAt(Instant.now().minusSeconds(100));

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired-refresh");

        when(staffUserRepository.findByRefreshToken("expired-refresh")).thenReturn(Optional.of(staffUser));

        assertThatThrownBy(() -> staffAuthService.refresh(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_unknownToken_throwsUnauthorized() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("unknown");

        when(staffUserRepository.findByRefreshToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffAuthService.refresh(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logout_clearsRefreshToken() {
        StaffUser staffUser = buildStaffUser();
        staffUser.setRefreshToken("some-token");

        when(staffUserRepository.findByIdAndActiveTrue(staffUser.getId())).thenReturn(Optional.of(staffUser));
        when(staffUserRepository.save(any())).thenReturn(staffUser);

        staffAuthService.logout(staffUser.getId());

        verify(staffUserRepository).save(staffUser);
        assertThat(staffUser.getRefreshToken()).isNull();
        assertThat(staffUser.getTokenExpiresAt()).isNull();
    }

    private StaffUser buildStaffUser() {
        return StaffUser.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .password("hashed")
                .role(StaffRole.ADMIN)
                .active(true)
                .build();
    }
}
