package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.CampaignBarrierAffectedDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignBarrierAffectedDifficulty.CampaignBarrierAffectedDifficultyId;

public interface CampaignBarrierAffectedDifficultyRepository
        extends JpaRepository<CampaignBarrierAffectedDifficulty, CampaignBarrierAffectedDifficultyId> {

    List<CampaignBarrierAffectedDifficulty> findByBarrier_Id(UUID barrierId);

    List<CampaignBarrierAffectedDifficulty> findByBarrier_IdIn(Collection<UUID> barrierIds);

    List<CampaignBarrierAffectedDifficulty> findByAffectedDifficulty_IdIn(Collection<UUID> difficultyIds);

    @Modifying
    @Query("delete from CampaignBarrierAffectedDifficulty a where a.barrier.id = :barrierId")
    int deleteByBarrier_Id(@Param("barrierId") UUID barrierId);

    @Modifying
    @Query("delete from CampaignBarrierAffectedDifficulty a "
            + "where a.barrier.id = :difficultyId or a.affectedDifficulty.id = :difficultyId")
    int deleteAllTouching(@Param("difficultyId") UUID difficultyId);
}
