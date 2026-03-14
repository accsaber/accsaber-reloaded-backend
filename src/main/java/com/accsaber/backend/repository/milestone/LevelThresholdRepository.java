package com.accsaber.backend.repository.milestone;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.milestone.LevelThreshold;

public interface LevelThresholdRepository extends JpaRepository<LevelThreshold, Integer> {

    List<LevelThreshold> findAllByOrderByLevelAsc();

    @Query("SELECT lt FROM LevelThreshold lt WHERE lt.level <= :level ORDER BY lt.level DESC LIMIT 1")
    Optional<LevelThreshold> findHighestTitleAtOrBelow(@Param("level") int level);
}
