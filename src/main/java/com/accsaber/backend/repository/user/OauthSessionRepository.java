package com.accsaber.backend.repository.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.user.OauthSession;

@Repository
public interface OauthSessionRepository extends JpaRepository<OauthSession, UUID> {

    Optional<OauthSession> findByRefreshToken(String refreshToken);

    void deleteByUserId(Long userId);
}
