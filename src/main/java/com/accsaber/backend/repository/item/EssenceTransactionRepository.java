package com.accsaber.backend.repository.item;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.item.EssenceTransaction;

public interface EssenceTransactionRepository extends JpaRepository<EssenceTransaction, UUID> {
}
