package com.accsaber.backend.service.staff;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.exception.ValidationException;
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
        StaffUser staffUser;

        if (isEmail) {
            staffUser = staffUserRepository.findByEmailIgnoreCaseAndActiveTrue(request.getIdentifier())
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
            if (!passwordEncoder.matches(request.getPassword(), staffUser.getPassword())) {
                throw new UnauthorizedException("Invalid credentials");
            }
        } else if (request.getRole() != null) {
            staffUser = staffUserRepository.findByUsernameIgnoreCaseAndRoleAndActiveTrue(
                    request.getIdentifier(), request.getRole())
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
            if (!passwordEncoder.matches(request.getPassword(), staffUser.getPassword())) {
                throw new UnauthorizedException("Invalid credentials");
            }
        } else {
            var matches = staffUserRepository.findByUsernameIgnoreCaseAndActiveTrue(request.getIdentifier());
            if (matches.isEmpty()) {
                throw new UnauthorizedException("Invalid credentials");
            }
            var passwordMatches = matches.stream()
                    .filter(s -> passwordEncoder.matches(request.getPassword(), s.getPassword()))
                    .toList();
            if (passwordMatches.isEmpty()) {
                throw new UnauthorizedException("Invalid credentials");
            }
            if (passwordMatches.size() > 1) {
                throw new ValidationException(
                        "Multiple accounts matched. Please specify a role.");
            }
            staffUser = passwordMatches.getFirst();
        }

        validateStatus(staffUser);

        return generateAndStoreTokens(staffUser);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        StaffUser staffUser = staffUserRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (staffUser.getTokenExpiresAt() == null || staffUser.getTokenExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        validateStatus(staffUser);

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

    private void validateStatus(StaffUser staffUser) {
        if (staffUser.getStatus() == StaffUserStatus.REQUESTED) {
            throw new ForbiddenException("Your staff access request is still pending approval");
        }
        if (staffUser.getStatus() == StaffUserStatus.DENIED) {
            throw new ForbiddenException("Your staff access request has been denied");
        }
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
