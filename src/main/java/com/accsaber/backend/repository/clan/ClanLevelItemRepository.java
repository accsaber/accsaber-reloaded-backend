package com.accsaber.backend.repository.clan;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.accsaber.backend.model.entity.clan.ClanLevelItem;

public interface ClanLevelItemRepository extends JpaRepository<ClanLevelItem, UUID> {

    @Query("SELECT li FROM ClanLevelItem li JOIN FETCH li.item ORDER BY li.level")
    List<ClanLevelItem> findAllWithItems();
}
