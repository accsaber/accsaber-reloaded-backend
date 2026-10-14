package com.accsaber.backend.repository.item;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.UserCrateOpen;

@Repository
public interface UserCrateOpenRepository extends JpaRepository<UserCrateOpen, UUID> {
}
