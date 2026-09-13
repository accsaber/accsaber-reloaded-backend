package com.accsaber.backend.repository.clan;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.ClanLevelWarMode;

public interface ClanLevelWarModeRepository extends JpaRepository<ClanLevelWarMode, ClanLevelWarMode.Key> {
}
