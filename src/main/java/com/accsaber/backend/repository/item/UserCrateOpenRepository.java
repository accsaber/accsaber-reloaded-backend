package com.accsaber.backend.repository.item;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.UserCrateOpen;

@Repository
public interface UserCrateOpenRepository extends JpaRepository<UserCrateOpen, UUID> {

    Page<UserCrateOpen> findByUser_Id(Long userId, Pageable pageable);

    Page<UserCrateOpen> findByCrateItem_Id(UUID crateItemId, Pageable pageable);

    Page<UserCrateOpen> findByRewardItem_Id(UUID rewardItemId, Pageable pageable);
}
