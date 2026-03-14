package com.accsaber.backend.controller.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.staff.LoginRequest;
import com.accsaber.backend.model.dto.request.staff.RefreshTokenRequest;
import com.accsaber.backend.model.dto.response.staff.AuthResponse;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.service.staff.StaffAuthService;

@ExtendWith(MockitoExtension.class)
class StaffAuthControllerTest {

    @Mock
    private StaffAuthService staffAuthService;

    @InjectMocks
    private StaffAuthController staffAuthController;

    @Test
    void login_validCredentials_returns200WithAuthResponse() {
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(3600L)
                .role(StaffRole.ADMIN)
                .build();
        when(staffAuthService.login(any())).thenReturn(authResponse);

        LoginRequest request = new LoginRequest();
        request.setIdentifier("admin");
        request.setPassword("password");

        ResponseEntity<AuthResponse> response = staffAuthController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("access-token");
        assertThat(response.getBody().getRole()).isEqualTo(StaffRole.ADMIN);
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        when(staffAuthService.login(any())).thenThrow(new UnauthorizedException("Invalid credentials"));

        LoginRequest request = new LoginRequest();
        request.setIdentifier("admin");
        request.setPassword("wrong");

        assertThatThrownBy(() -> staffAuthController.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void refresh_validToken_returns200WithNewAccessToken() {
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .expiresIn(3600L)
                .role(StaffRole.RANKING)
                .build();
        when(staffAuthService.refresh(any())).thenReturn(authResponse);

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        ResponseEntity<AuthResponse> response = staffAuthController.refresh(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("new-access-token");
    }

    @Test
    void refresh_invalidToken_throwsUnauthorized() {
        when(staffAuthService.refresh(any())).thenThrow(new UnauthorizedException("Token expired or not found"));

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired-token");

        assertThatThrownBy(() -> staffAuthController.refresh(request))
                .isInstanceOf(UnauthorizedException.class);
    }
}
