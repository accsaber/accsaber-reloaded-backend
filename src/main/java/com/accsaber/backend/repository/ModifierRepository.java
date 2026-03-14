package com.accsaber.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.Modifier;

@Repository
public interface ModifierRepository extends JpaRepository<Modifier, UUID> {

    List<Modifier> findByActiveTrue();
}
