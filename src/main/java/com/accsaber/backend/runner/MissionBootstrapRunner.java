package com.accsaber.backend.runner;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.mission.MissionAssignmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class MissionBootstrapRunner implements ApplicationRunner {

    private final MissionAssignmentService missionAssignmentService;

    @Override
    public void run(ApplicationArguments args) {
        boolean ran = missionAssignmentService.bootstrapIfEmpty();
        if (!ran) {
            log.debug("Mission bootstrap skipped - missions already present");
        }
    }
}
