package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.user.UserCategorySkill;

public record MissionAssignmentContext(
        Long userId,
        List<Category> activeCategories,
        Map<UUID, UserCategorySkill> skillByCategoryId,
        Map<UUID, Long> rankedPlaysByCategoryId,
        BigDecimal rollingDailyXp) {
}
