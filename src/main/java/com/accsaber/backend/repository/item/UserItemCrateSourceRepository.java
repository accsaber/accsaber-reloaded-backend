package com.accsaber.backend.repository.item;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.item.UserItemCrateSource;

public interface UserItemCrateSourceRepository extends JpaRepository<UserItemCrateSource, String> {

    @Query("""
            SELECT cs FROM UserItemCrateSource cs
            JOIN FETCH cs.crateItem ci
            JOIN FETCH ci.type
            WHERE cs.sourceId IN :sourceIds
            """)
    List<UserItemCrateSource> findAllWithCrateItem(@Param("sourceIds") Collection<String> sourceIds);
}
