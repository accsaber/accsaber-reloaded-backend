package com.accsaber.backend.repository.clan.war;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.war.ClanWarSide;

public interface ClanWarSideRepository extends JpaRepository<ClanWarSide, ClanWarSide.Key> {
}
