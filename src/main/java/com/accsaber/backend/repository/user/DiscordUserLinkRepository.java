package com.accsaber.backend.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.user.DiscordUserLink;

@Repository
public interface DiscordUserLinkRepository extends JpaRepository<DiscordUserLink, String> {

    Optional<DiscordUserLink> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
