package com.accsaber.backend.repository.clan;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.ClanXpGrant;

public interface ClanXpGrantRepository extends JpaRepository<ClanXpGrant, UUID> {
}
