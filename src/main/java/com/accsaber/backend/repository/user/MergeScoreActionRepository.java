package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.user.MergeScoreAction;

public interface MergeScoreActionRepository extends JpaRepository<MergeScoreAction, UUID> {

    List<MergeScoreAction> findByLink_Id(UUID linkId);

    void deleteByLink_Id(UUID linkId);
}
