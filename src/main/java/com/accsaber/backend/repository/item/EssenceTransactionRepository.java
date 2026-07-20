package com.accsaber.backend.repository.item;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.EssenceTransaction;

@Repository
public interface EssenceTransactionRepository extends JpaRepository<EssenceTransaction, UUID> {

    Page<EssenceTransaction> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
