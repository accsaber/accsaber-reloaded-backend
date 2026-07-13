package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.mission.MissionTemplate;

public record MissionPoolCache(
        List<MissionTemplate> daily,
        List<MissionTemplate> weekly,
        List<Item> poolableItems,
        Item eventCrate,
        ConcurrentHashMap<UUID, BigDecimal> mapWrApByDifficulty) {
}
