package com.accsaber.backend.repository.map;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.BatchStatus;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

        Optional<Batch> findFirstByStatusOrderByReleasedAtDesc(BatchStatus status);

        @Query(value = """
                        SELECT b FROM Batch b
                        LEFT JOIN b.difficulties d ON d.active = true
                        WHERE (:status IS NULL OR b.status = :status)
                        AND (CAST(:search AS string) IS NULL
                                OR LOWER(b.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
                        GROUP BY b
                        """, countQuery = """
                        SELECT COUNT(b) FROM Batch b
                        WHERE (:status IS NULL OR b.status = :status)
                        AND (CAST(:search AS string) IS NULL
                                OR LOWER(b.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
                        """)
        Page<Batch> findFiltered(
                        @Param("status") BatchStatus status,
                        @Param("search") String search,
                        Pageable pageable);
}
