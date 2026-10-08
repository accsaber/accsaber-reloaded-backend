package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanXpGrantRepository;

@ExtendWith(MockitoExtension.class)
class ClanPlayXpServiceTest {

    private static final UUID CLAN_ID = UUID.randomUUID();
    private static final UUID SCORE_ID = UUID.randomUUID();
    private static final Instant SET_AT = Instant.parse("2026-10-08T12:00:00Z");

    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanXpGrantRepository grantRepository;
    @Mock
    private ClanLevelService levelService;

    private ClanPlayXpService service;

    @BeforeEach
    void setUp() {
        service = new ClanPlayXpService(memberRepository, grantRepository, levelService, new ClanProperties());
    }

    private ScoreSubmittedEvent play(double xp, boolean partial) {
        return new ScoreSubmittedEvent(ScoreResponse.builder().id(SCORE_ID).userId("7").xpGained(xp)
                .partial(partial).timeSet(SET_AT).build());
    }

    private void memberSince(Instant joinedAt) {
        ClanMember member = ClanMember.builder().clan(Clan.builder().id(CLAN_ID).build()).joinedAt(joinedAt).build();
        when(memberRepository.findOpenByUserId(7L)).thenReturn(Optional.of(member));
    }

    @Test
    void aPlayPaysTheClanAFlatAmountKeyedByTheScore() {
        memberSince(SET_AT.minus(1, ChronoUnit.DAYS));

        service.onScoreSubmitted(play(812.0, false));

        verify(levelService).grantXp(CLAN_ID, new ClanXpAward(3.0, ClanXpSource.play, SCORE_ID.toString(), true));
    }

    @Test
    void theAmountIgnoresHowMuchXpThePlayEarned() {
        memberSince(SET_AT.minus(1, ChronoUnit.DAYS));

        service.onScoreSubmitted(play(25.0, false));

        verify(levelService).grantXp(CLAN_ID, new ClanXpAward(3.0, ClanXpSource.play, SCORE_ID.toString(), true));
    }

    @Test
    void partialsAndPlaysThatEarnedNothingPayNothing() {
        service.onScoreSubmitted(play(10.0, true));
        service.onScoreSubmitted(play(0.0, false));

        verify(levelService, never()).grantXp(any(), any());
    }

    @Test
    void aPlaySetBeforeJoiningPaysNothing() {
        memberSince(SET_AT.plus(1, ChronoUnit.HOURS));

        service.onScoreSubmitted(play(500.0, false));

        verify(levelService, never()).grantXp(any(), any());
    }

    @Test
    void theSweepGrantsEveryMissedPlay() {
        UUID other = UUID.randomUUID();
        Instant from = SET_AT.minus(48, ChronoUnit.HOURS);
        when(grantRepository.findUngrantedPlays(from, SET_AT)).thenReturn(List.of(missed(SCORE_ID), missed(other)));
        when(levelService.grantXp(eq(CLAN_ID), any())).thenReturn(true, false);

        assertThat(service.grantMissed(from, SET_AT)).isEqualTo(1);
        verify(levelService).grantXp(CLAN_ID, new ClanXpAward(3.0, ClanXpSource.play, other.toString(), true));
    }

    private ClanXpGrantRepository.UngrantedPlayView missed(UUID scoreId) {
        return new ClanXpGrantRepository.UngrantedPlayView() {
            public UUID getClanId() {
                return CLAN_ID;
            }

            public UUID getScoreId() {
                return scoreId;
            }
        };
    }
}
