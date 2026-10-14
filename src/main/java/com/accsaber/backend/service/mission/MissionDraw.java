package com.accsaber.backend.service.mission;

import java.time.Instant;
import java.util.Random;

import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionPool;

public record MissionDraw(MissionAssignmentContext ctx, Instant expiresAt, MissionPool pool, Random rng,
        MissionPoolCache cache, MissionBand forcedBand) {
}
