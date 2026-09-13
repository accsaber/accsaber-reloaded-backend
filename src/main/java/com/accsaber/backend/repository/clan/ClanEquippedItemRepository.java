package com.accsaber.backend.repository.clan;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.ClanEquippedItem;

public interface ClanEquippedItemRepository extends JpaRepository<ClanEquippedItem, ClanEquippedItem.Key> {
}
