package com.accsaber.backend.scheduler;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.service.clan.ClanStrengthService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClanStrengthScheduler {

    private static final int CHUNK_SIZE = 500;

    private final ClanRepository clanRepository;
    private final ClanStrengthService strengthService;

    @Scheduled(fixedDelay = 600_000, initialDelay = 90_000)
    public void recomputeAll() {
        Page<UUID> chunk;
        int page = 0;
        do {
            chunk = clanRepository.findActiveIds(PageRequest.of(page++, CHUNK_SIZE));
            strengthService.recompute(chunk.getContent());
        } while (chunk.hasNext());
    }
}
