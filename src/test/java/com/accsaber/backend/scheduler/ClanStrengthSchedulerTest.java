package com.accsaber.backend.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.service.clan.ClanStrengthService;

@ExtendWith(MockitoExtension.class)
class ClanStrengthSchedulerTest {

    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanStrengthService strengthService;

    @InjectMocks
    private ClanStrengthScheduler scheduler;

    @Test
    void everyActiveClanIsRecomputedChunkByChunk() {
        List<UUID> firstChunk = List.of(UUID.randomUUID());
        List<UUID> secondChunk = List.of(UUID.randomUUID());
        when(clanRepository.findActiveIds(PageRequest.of(0, 500)))
                .thenReturn(new PageImpl<>(firstChunk, PageRequest.of(0, 500), 501));
        when(clanRepository.findActiveIds(PageRequest.of(1, 500)))
                .thenReturn(new PageImpl<>(secondChunk, PageRequest.of(1, 500), 501));

        scheduler.recomputeAll();

        verify(strengthService).recompute(firstChunk);
        verify(strengthService).recompute(secondChunk);
    }
}
