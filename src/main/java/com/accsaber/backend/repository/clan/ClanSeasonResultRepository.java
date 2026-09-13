package com.accsaber.backend.repository.clan;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.ClanSeasonResult;

public interface ClanSeasonResultRepository extends JpaRepository<ClanSeasonResult, ClanSeasonResult.Key> {
}
