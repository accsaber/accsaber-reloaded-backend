package com.accsaber.backend.service.mission;

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
        return userMissionRepository.findAllActiveByUser(userId);
    }

    public List<UserMission> listActiveByPool(Long userId, MissionPool pool) {
        return userMissionRepository.findByUser_IdAndPoolAndStatus(userId, pool, MissionStatus.active);
    }

    public List<UserMission> listCompleted(Long userId) {
        return userMissionRepository.findByUser_IdAndStatus(userId, MissionStatus.completed);
    }
}
