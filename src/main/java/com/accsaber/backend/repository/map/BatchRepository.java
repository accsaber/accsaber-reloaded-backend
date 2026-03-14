package com.accsaber.backend.repository.map;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.BatchStatus;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

    Page<Batch> findByStatus(BatchStatus status, Pageable pageable);
}
