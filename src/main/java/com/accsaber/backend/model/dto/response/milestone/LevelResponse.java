package com.accsaber.backend.model.dto.response.milestone;

import com.accsaber.backend.util.LevelCurve;
import com.accsaber.backend.util.Rounding;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LevelResponse {

    private int level;
    private String title;
    private Double totalXp;
    private Double xpForCurrentLevel;
    private Double xpForNextLevel;
    private Double progressPercent;

    public static LevelResponse of(LevelCurve.Progress progress, double totalXp, String title) {
        double percent = progress.xpForNextLevel() > 0
                ? Rounding.round(progress.xpIntoLevel() * 100.0 / progress.xpForNextLevel(), 2)
                : 0.0;
        return LevelResponse.builder()
                .level(progress.level())
                .title(title)
                .totalXp(totalXp)
                .xpForCurrentLevel(progress.xpIntoLevel())
                .xpForNextLevel(progress.xpForNextLevel())
                .progressPercent(percent)
                .build();
    }
}
