package com.accsaber.backend.repository.item;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.CrateContent;
import com.accsaber.backend.model.entity.item.CrateContent.CrateContentId;

@Repository
public interface CrateContentRepository extends JpaRepository<CrateContent, CrateContentId> {

    List<CrateContent> findByCrateItem_Id(UUID crateItemId);

    List<CrateContent> findByCrateItem_IdAndRewardItem_VisibleTrue(UUID crateItemId);
}
