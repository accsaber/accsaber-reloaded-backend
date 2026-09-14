package com.accsaber.backend.repository.clan.war;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;

public interface ClanWarRewardItemRepository extends JpaRepository<ClanWarRewardItem, UUID> {

    @Query("SELECT r FROM ClanWarRewardItem r JOIN FETCH r.item i JOIN FETCH i.type t LEFT JOIN FETCH t.parentType WHERE r.active")
    List<ClanWarRewardItem> findActiveWithItems();

    @Query("SELECT r FROM ClanWarRewardItem r JOIN FETCH r.item i JOIN FETCH i.type ORDER BY r.active DESC, r.id")
    List<ClanWarRewardItem> findAllWithItems();
}
