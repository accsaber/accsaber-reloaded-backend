package com.accsaber.backend.repository.clan.war;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.war.ClanWarHit;

public interface ClanWarHitRepository extends JpaRepository<ClanWarHit, UUID> {
}
