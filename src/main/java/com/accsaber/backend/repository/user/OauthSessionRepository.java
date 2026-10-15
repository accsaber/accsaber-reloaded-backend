package com.accsaber.backend.repository.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.user.OauthSession;

public interface OauthSessionRepository extends JpaRepository<OauthSession, UUID> {

    Optional<OauthSession> findByRefreshToken(String refreshToken);

    void deleteByUserId(Long userId);

    void deleteByConnection_Id(UUID connectionId);
}
