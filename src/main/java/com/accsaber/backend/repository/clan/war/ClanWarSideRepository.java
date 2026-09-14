package com.accsaber.backend.repository.clan.war;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarSide;

public interface ClanWarSideRepository extends JpaRepository<ClanWarSide, ClanWarSide.Key> {

    @Query("SELECT s FROM ClanWarSide s LEFT JOIN FETCH s.leadUser WHERE s.war.id IN :warIds")
    List<ClanWarSide> findByWarIds(@Param("warIds") Collection<UUID> warIds);

    Optional<ClanWarSide> findByWar_IdAndClan_Id(UUID warId, UUID clanId);
}
