package com.accsaber.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.repository.clan.ClanXpGrantRepository;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanXpAward;

@ExtendWith(MockitoExtension.class)
class ClanPlayXpSchedulerTest {

    @Mock
    private ClanXpGrantRepository grantRepository;
    @Mock
    private ClanLevelService levelService;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanPlayXpScheduler scheduler;

    @BeforeEach
    void setUp() {
        clanProperties.setPlayXpShare(0.1);
        scheduler = new ClanPlayXpScheduler(grantRepository, levelService, clanProperties);
    }

    private ClanXpGrantRepository.MemberPlayXpView row(UUID clanId, double xp) {
        return new ClanXpGrantRepository.MemberPlayXpView() {
            public UUID getClanId() {
                return clanId;
            }

            public double getXp() {
                return xp;
            }
        };
    }

    @Test
    void aDayBanksTheShareOfMemberXpKeyedByTheDate() {
        UUID clanId = UUID.randomUUID();
        when(grantRepository.sumUngrantedMemberPlayXp(Instant.parse("2026-09-12T00:00:00Z"),
                Instant.parse("2026-09-13T00:00:00Z"), "2026-09-12")).thenReturn(List.of(row(clanId, 4000.0)));
        when(levelService.grantXp(eq(clanId), any())).thenReturn(true);

        int granted = scheduler.grantDay(LocalDate.parse("2026-09-12"));

        ArgumentCaptor<ClanXpAward> award = ArgumentCaptor.forClass(ClanXpAward.class);
        verify(levelService).grantXp(eq(clanId), award.capture());
        assertThat(granted).isEqualTo(1);
        assertThat(award.getValue().rawAmount()).isEqualTo(400.0);
        assertThat(award.getValue().source()).isEqualTo(ClanXpSource.daily_play);
        assertThat(award.getValue().sourceId()).isEqualTo("2026-09-12");
        assertThat(award.getValue().rosterScaled()).isTrue();
    }

    @Test
    void oneFailingClanDoesNotStopTheRest() {
        UUID broken = UUID.randomUUID();
        UUID fine = UUID.randomUUID();
        when(grantRepository.sumUngrantedMemberPlayXp(any(), any(), anyString()))
                .thenReturn(List.of(row(broken, 100.0), row(fine, 100.0)));
        when(levelService.grantXp(eq(broken), any())).thenThrow(new IllegalStateException("boom"));
        when(levelService.grantXp(eq(fine), any())).thenReturn(true);

        assertThat(scheduler.grantDay(LocalDate.parse("2026-09-12"))).isEqualTo(1);
    }

    @Test
    void theDailyRunWalksBackAWeek() {
        scheduler.grantDailyPlayXp();

        verify(grantRepository, times(7)).sumUngrantedMemberPlayXp(any(), any(), anyString());
    }
}
