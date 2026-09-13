package com.accsaber.backend.repository.clan;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.ClanItem;

public interface ClanItemRepository extends JpaRepository<ClanItem, ClanItem.Key> {
}
