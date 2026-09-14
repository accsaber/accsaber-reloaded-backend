package com.accsaber.backend.repository.clan.war;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;

public interface ClanWarParticipantRepository extends JpaRepository<ClanWarParticipant, ClanWarParticipant.Key> {
}
