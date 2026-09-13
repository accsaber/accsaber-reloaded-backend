package com.accsaber.backend.repository.clan;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.Clan;

public interface ClanRepository extends JpaRepository<Clan, UUID> {
}
