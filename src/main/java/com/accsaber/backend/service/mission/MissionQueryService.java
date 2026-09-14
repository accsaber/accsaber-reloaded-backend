package com.accsaber.backend.service.mission;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.repository.mission.UserMissionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionQueryService {

    private final UserMissionRepository userMissionRepository;

    public List<UserMission> listActive(Long userId) {
        return withoutClanRows(userMissionRepository.findCurrentByUser(userId, Instant.now()));
    }

    public List<UserMission> listActiveByPool(Long userId, MissionPool pool) {
        return userMissionRepository.findCurrentByUserAndPool(userId, pool, Instant.now());
    }

    public List<UserMission> listCompleted(Long userId) {
        return withoutClanRows(userMissionRepository.findByUser_IdAndStatus(userId, MissionStatus.completed));
    }

    private static List<UserMission> withoutClanRows(List<UserMission> missions) {
        return missions.stream().filter(m -> m.getPool() != MissionPool.clan).toList();
    }
}
