package com.accsaber.backend.service.staff;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.staff.LoginRequest;
import com.accsaber.backend.model.dto.request.staff.RefreshTokenRequest;
import com.accsaber.backend.model.dto.response.staff.AuthResponse;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;
import com.accsaber.backend.repository.staff.StaffUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffAuthService {

    private final StaffUserRepository staffUserRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${accsaber.jwt.access-token-ttl}")
    private long accessTokenTtl;

    @Value("${accsaber.jwt.refresh-token-ttl}")
    private long refreshTokenTtl;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        boolean isEmail = request.getIdentifier().contains("@");
        StaffUser staffUser = (isEmail
                ? staffUserRepository.findByEmailAndActiveTrue(request.getIdentifier())
                : staffUserRepository.findByUsernameAndActiveTrue(request.getIdentifier()))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), staffUser.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (staffUser.getStatus() != StaffUserStatus.ACCEPTED) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return generateAndStoreTokens(staffUser);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        StaffUser staffUser = staffUserRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (staffUser.getTokenExpiresAt() == null || staffUser.getTokenExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        if (staffUser.getStatus() != StaffUserStatus.ACCEPTED) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        return generateAndStoreTokens(staffUser);
    }

    @Transactional
    public void logout(UUID staffId) {
        staffUserRepository.findByIdAndActiveTrue(staffId).ifPresent(staffUser -> {
            staffUser.setRefreshToken(null);
            staffUser.setTokenExpiresAt(null);
            staffUserRepository.save(staffUser);
        });
    }

    private AuthResponse generateAndStoreTokens(StaffUser staffUser) {
        String accessToken = jwtService.generateAccessToken(staffUser);
        String refreshToken = jwtService.generateRefreshToken();

        staffUser.setRefreshToken(refreshToken);
        staffUser.setTokenExpiresAt(Instant.now().plusSeconds(refreshTokenTtl));
        staffUserRepository.save(staffUser);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTokenTtl)
                .role(staffUser.getRole())
                .build();
    }
}
