package com.accsaber.backend.repository.clan.war;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.clan.war.ClanWarLoan;

public interface ClanWarLoanRepository extends JpaRepository<ClanWarLoan, UUID> {
}
