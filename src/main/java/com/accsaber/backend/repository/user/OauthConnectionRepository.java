package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.user.OauthConnection;

@Repository
public interface OauthConnectionRepository extends JpaRepository<OauthConnection, UUID> {

    Optional<OauthConnection> findByProviderAndProviderUserIdAndActiveTrue(String provider, String providerUserId);

    Optional<OauthConnection> findByUserIdAndProviderAndActiveTrue(Long userId, String provider);

    List<OauthConnection> findByUserIdAndActiveTrue(Long userId);

    boolean existsByProviderAndProviderUserIdAndActiveTrue(String provider, String providerUserId);

    boolean existsByUserIdAndProviderAndActiveTrue(Long userId, String provider);
}
