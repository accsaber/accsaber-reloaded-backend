package com.accsaber.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.Modifier;

public interface ModifierRepository extends JpaRepository<Modifier, UUID> {

    List<Modifier> findByActiveTrue();
}
