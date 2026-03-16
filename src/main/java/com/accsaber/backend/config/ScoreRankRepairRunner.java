package com.accsaber.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.score.ScoreRankingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ScoreRankRepairRunner implements ApplicationRunner {

    private final ScoreRankingService scoreRankingService;

    @Override
    public void run(ApplicationArguments args) {
        scoreRankingService.reassignAllRanks();
    }
}
