package com.accsaber.backend.repository.clan.war;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarPoolEntry;
import com.accsaber.backend.model.entity.map.MapDifficulty;

public interface ClanWarPoolEntryRepository extends JpaRepository<ClanWarPoolEntry, ClanWarPoolEntry.Key> {

    String LEGAL_DIFFICULTIES = """
            FROM map_difficulties md
            JOIN categories cat ON cat.id = md.category_id AND cat.active
            JOIN map_difficulty_complexities mc ON mc.map_difficulty_id = md.id AND mc.active
            WHERE md.active AND md.status = 'ranked'
              AND (CAST(:categoryId AS uuid) IS NULL OR md.category_id = CAST(:categoryId AS uuid))
              AND (CAST(:complexityMin AS double precision) IS NULL
                   OR mc.complexity >= CAST(:complexityMin AS double precision))
              AND (CAST(:complexityMax AS double precision) IS NULL
                   OR mc.complexity <= CAST(:complexityMax AS double precision))
            """;

    @Query("""
            SELECT p FROM ClanWarPoolEntry p LEFT JOIN FETCH p.pickedByClan
            WHERE p.war.id = :warId
            """)
    List<ClanWarPoolEntry> findByWarId(@Param("warId") UUID warId);

    @Query("""
            SELECT d FROM MapDifficulty d JOIN FETCH d.map
            WHERE d.id IN (SELECT p.mapDifficulty.id FROM ClanWarPoolEntry p WHERE p.war.id = :warId)
            """)
    List<MapDifficulty> findDifficulties(@Param("warId") UUID warId);

    @Query(value = "SELECT md.id " + LEGAL_DIFFICULTIES + " AND md.id IN (:ids)", nativeQuery = true)
    List<UUID> findLegal(@Param("ids") Collection<UUID> ids, @Param("categoryId") UUID categoryId,
            @Param("complexityMin") Double complexityMin, @Param("complexityMax") Double complexityMax);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO clan_war_pool (war_id, map_difficulty_id, picked_by_clan_id, source)
            SELECT CAST(:warId AS uuid), md.id, CAST(:pickedBy AS uuid), :source
            """ + LEGAL_DIFFICULTIES + """
              AND NOT EXISTS (SELECT 1 FROM clan_war_pool p
                              WHERE p.war_id = CAST(:warId AS uuid) AND p.map_difficulty_id = md.id)
            ORDER BY random()
            LIMIT :count
            """, nativeQuery = true)
    int insertRandom(@Param("warId") UUID warId, @Param("pickedBy") UUID pickedBy, @Param("source") String source,
            @Param("count") int count, @Param("categoryId") UUID categoryId,
            @Param("complexityMin") Double complexityMin, @Param("complexityMax") Double complexityMax);
}
