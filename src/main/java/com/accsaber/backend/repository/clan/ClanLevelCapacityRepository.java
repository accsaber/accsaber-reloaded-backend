package com.accsaber.backend.repository.clan;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.ClanLevelCapacity;

public interface ClanLevelCapacityRepository extends JpaRepository<ClanLevelCapacity, ClanLevelCapacity.Key> {
}
