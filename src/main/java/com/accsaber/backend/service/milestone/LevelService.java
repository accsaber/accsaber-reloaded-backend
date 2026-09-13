package com.accsaber.backend.service.milestone;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.milestone.UpsertLevelThresholdRequest;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.dto.response.milestone.LevelThresholdResponse;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.milestone.LevelThreshold;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.milestone.LevelThresholdRepository;
import com.accsaber.backend.util.LevelCurve;
import com.accsaber.backend.util.Rounding;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LevelService {

    private static final UUID LEVEL_CURVE_ID = UUID.fromString("acc00000-0000-0000-0000-000000000004");
    private static final int LEVEL_COST_CAP = 100;

    private final LevelThresholdRepository levelThresholdRepository;
    private final CurveRepository curveRepository;
    private final ItemRepository itemRepository;

    private volatile LevelCurve cachedLevelCurve;
    private final Object curveLock = new Object();

    public LevelResponse calculateLevel(Double totalXp) {
        double xp = totalXp == null || totalXp <= 0 ? 0.0 : totalXp;
        LevelCurve.Progress progress = getLevelCurve().progressAt(xp);
        double percent = progress.xpForNextLevel() > 0
                ? Rounding.round(progress.xpIntoLevel() * 100.0 / progress.xpForNextLevel(), 2)
                : 0.0;
        String title = xp <= 0 ? null
                : levelThresholdRepository.findHighestTitleAtOrBelow(progress.level())
                        .map(LevelThreshold::getTitle)
                        .orElse(null);

        return LevelResponse.builder()
                .level(progress.level())
                .title(title)
                .totalXp(xp)
                .xpForCurrentLevel(progress.xpIntoLevel())
                .xpForNextLevel(progress.xpForNextLevel())
                .progressPercent(percent)
                .build();
    }

    public double xpForLevel(int n) {
        return getLevelCurve().xpForLevel(n);
    }

    public List<LevelThreshold> getAllThresholds() {
        return levelThresholdRepository.findAllByOrderByLevelAsc();
    }

    public List<LevelThresholdResponse> listThresholds() {
        return levelThresholdRepository.findAllByOrderByLevelAsc().stream()
                .map(LevelService::toResponse)
                .toList();
    }

    public LevelThresholdResponse findThreshold(int level) {
        return levelThresholdRepository.findById(level)
                .map(LevelService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("LevelThreshold", level));
    }

    @Transactional
    public LevelThresholdResponse upsertThreshold(int level, UpsertLevelThresholdRequest request) {
        LevelThreshold threshold = levelThresholdRepository.findById(level)
                .orElseGet(() -> LevelThreshold.builder().level(level).build());
        threshold.setTitle(request.getTitle());
        threshold.setAwardsItem(loadItem(request.getAwardsItemId()));
        return toResponse(levelThresholdRepository.save(threshold));
    }

    @Transactional
    public void deleteThreshold(int level) {
        if (!levelThresholdRepository.existsById(level)) {
            throw new ResourceNotFoundException("LevelThreshold", level);
        }
        levelThresholdRepository.deleteById(level);
    }

    private Item loadItem(UUID itemId) {
        if (itemId == null)
            return null;
        return itemRepository.findByIdAndActiveTrue(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
    }

    private static LevelThresholdResponse toResponse(LevelThreshold t) {
        return LevelThresholdResponse.builder()
                .level(t.getLevel())
                .title(t.getTitle())
                .awardsItemId(t.getAwardsItem() != null ? t.getAwardsItem().getId() : null)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    public void evictLevelCurveCache() {
        cachedLevelCurve = null;
    }

    private LevelCurve getLevelCurve() {
        LevelCurve curve = cachedLevelCurve;
        if (curve == null) {
            synchronized (curveLock) {
                curve = cachedLevelCurve;
                if (curve == null) {
                    Curve stored = curveRepository.findById(LEVEL_CURVE_ID)
                            .orElseThrow(() -> new IllegalStateException("Level curve not found"));
                    curve = new LevelCurve(stored.getXParameterValue(), stored.getYParameterValue(), LEVEL_COST_CAP);
                    cachedLevelCurve = curve;
                }
            }
        }
        return curve;
    }
}
