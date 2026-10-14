package com.accsaber.backend.repository.item;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.CrateContent;
import com.accsaber.backend.model.entity.item.CrateContent.CrateContentId;

@Repository
public interface CrateContentRepository extends JpaRepository<CrateContent, CrateContentId> {

    List<CrateContent> findByCrateItem_Id(UUID crateItemId);

    List<CrateContent> findByCrateItem_IdAndRewardItem_VisibleTrue(UUID crateItemId);

    @Query("SELECT COALESCE(SUM(c.dropWeight), 0) FROM CrateContent c WHERE c.crateItem.id = :crateItemId")
    long sumDropWeightByCrateItemId(@Param("crateItemId") UUID crateItemId);
}
