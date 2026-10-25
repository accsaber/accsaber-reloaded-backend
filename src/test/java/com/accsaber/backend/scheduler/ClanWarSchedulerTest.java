package com.accsaber.backend.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.service.clan.war.ClanWarCombatService;
import com.accsaber.backend.service.clan.war.ClanWarPoolService;
import com.accsaber.backend.service.clan.war.ClanWarRosterService;
import com.accsaber.backend.service.clan.war.ClanWarService;
import com.accsaber.backend.service.clan.war.ClanWarSettlementService;

@ExtendWith(MockitoExtension.class)
class ClanWarSchedulerTest {

    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanWarCombatService combatService;
    @Mock
    private ClanWarPoolService poolService;
    @Mock
    private ClanWarRosterService rosterService;
    @Mock
    private ClanWarService warService;
    @Mock
    private ClanWarSettlementService settlementService;

    private ClanWarScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ClanWarScheduler(warRepository, combatService, poolService, rosterService, warService, settlementService,
                new ClanProperties());
    }

    @Test
    void eachDueWarMovesOnAndOneFailureDoesNotStopTheRest() {
        UUID brokenPick = UUID.randomUUID();
        UUID duePick = UUID.randomUUID();
        UUID dueStart = UUID.randomUUID();
        UUID quiet = UUID.randomUUID();
        when(warRepository.findPicksDue(any())).thenReturn(List.of(brokenPick, duePick));
        when(warRepository.findStartsDue(any())).thenReturn(List.of(dueStart));
        when(warRepository.findQuietSince(any())).thenReturn(List.of(quiet));
        UUID retreated = UUID.randomUUID();
        when(warRepository.findRetreatsDue(any())).thenReturn(List.of(retreated));
        UUID guardsDue = UUID.randomUUID();
        when(warRepository.findWithBrokenGuards()).thenReturn(List.of(guardsDue));
        UUID unsettled = UUID.randomUUID();
        when(warRepository.findEndedUnsettled(20)).thenReturn(List.of(unsettled));
        doThrow(new IllegalStateException("boom")).when(poolService).closePicks(brokenPick);

        scheduler.advance();

        verify(poolService).closePicks(duePick);
        verify(rosterService).start(dueStart);
        verify(warService).end(quiet, ClanWarOutcome.drawn);
        verify(warService).end(retreated, ClanWarOutcome.retreated);
        verify(rosterService).restoreGuards(eq(guardsDue), any(), any());
        verify(settlementService).settle(unsettled);
    }
}
