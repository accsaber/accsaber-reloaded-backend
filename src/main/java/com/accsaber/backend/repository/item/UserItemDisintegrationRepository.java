package com.accsaber.backend.repository.item;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.item.UserItemDisintegration;

public interface UserItemDisintegrationRepository extends JpaRepository<UserItemDisintegration, UUID> {
}
