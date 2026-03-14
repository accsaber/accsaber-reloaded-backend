package com.accsaber.backend.service.map;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.CriteriaStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.repository.map.MapDifficultyRepository;

import lombok.RequiredArgsConstructor;

// Separate as this will also be used to invoke the criteria checker script.
@Service
@RequiredArgsConstructor
public class CriteriaService {

    private final MapDifficultyRepository mapDifficultyRepository;

    @Transactional
    public void updateCriteriaStatus(UUID mapDifficultyId, CriteriaStatus status) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
        difficulty.setCriteriaStatus(status);
        mapDifficultyRepository.save(difficulty);
    }
}
