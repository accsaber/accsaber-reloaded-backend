package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.user.UserNameHistory;

public interface UserNameHistoryRepository extends JpaRepository<UserNameHistory, UUID> {

    List<UserNameHistory> findByUser_IdOrderByChangedAtDesc(Long userId);
}
