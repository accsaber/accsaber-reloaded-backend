package com.accsaber.backend.repository.clan;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.ClanSeasonStanding;

public interface ClanSeasonStandingRepository extends JpaRepository<ClanSeasonStanding, ClanSeasonStanding.Key> {
}
