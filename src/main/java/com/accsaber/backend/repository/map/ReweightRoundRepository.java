package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.ReweightRound;

public interface ReweightRoundRepository extends JpaRepository<ReweightRound, UUID> {

        @Query("""
                        SELECT r FROM ReweightRound r JOIN FETCH r.category c
                        WHERE c.id = :categoryId
                        ORDER BY r.createdAt, r.id
                        """)
        List<ReweightRound> findByCategoryOldestFirst(@Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT r FROM ReweightRound r JOIN FETCH r.category c
                        WHERE c.active = true
                        ORDER BY r.createdAt, c.code, r.id
                        """)
        List<ReweightRound> findLiveCategoriesOldestFirst();
}
