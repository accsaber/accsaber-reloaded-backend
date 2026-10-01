package com.accsaber.backend.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.repository.clan.ClanSeasonRepository;
import com.accsaber.backend.service.clan.ClanSeasonService;

@ExtendWith(MockitoExtension.class)
class ClanSeasonSchedulerTest {

    @Mock
    private ClanSeasonRepository seasonRepository;
    @Mock
    private ClanSeasonService seasonService;

    @InjectMocks
    private ClanSeasonScheduler scheduler;

    @Test
    void endedSeasonsCloseBeforeTheNextOneOpens() {
        UUID ended = UUID.randomUUID();
        when(seasonRepository.findEndedUnclosedIds(any())).thenReturn(List.of(ended));

        scheduler.rollSeasons();

        InOrder order = inOrder(seasonService);
        order.verify(seasonService).close(ended);
        order.verify(seasonService).ensureCurrent();
    }

    @Test
    void aFailedCloseDoesNotOpenANewSeasonOnTopOfIt() {
        UUID ended = UUID.randomUUID();
        when(seasonRepository.findEndedUnclosedIds(any())).thenReturn(List.of(ended));
        doThrow(new IllegalStateException("boom")).when(seasonService).close(ended);

        scheduler.rollSeasons();

        verify(seasonService, never()).ensureCurrent();
    }
}
