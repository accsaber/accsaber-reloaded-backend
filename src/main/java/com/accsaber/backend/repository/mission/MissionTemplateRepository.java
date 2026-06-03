package com.accsaber.backend.repository.mission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionTemplate;

public interface MissionTemplateRepository extends JpaRepository<MissionTemplate, UUID> {

    List<MissionTemplate> findByActiveTrue();

    @EntityGraph(attributePaths = "xpCurve")
    List<MissionTemplate> findByPoolAndActiveTrue(MissionPool pool);

    Optional<MissionTemplate> findByCode(String code);
}
