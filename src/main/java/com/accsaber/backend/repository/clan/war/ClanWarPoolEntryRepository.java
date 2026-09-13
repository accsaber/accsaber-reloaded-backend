package com.accsaber.backend.repository.clan.war;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.war.ClanWarPoolEntry;

public interface ClanWarPoolEntryRepository extends JpaRepository<ClanWarPoolEntry, ClanWarPoolEntry.Key> {
}
