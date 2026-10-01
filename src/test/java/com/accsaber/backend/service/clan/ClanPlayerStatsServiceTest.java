package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.clan.ClanStatsResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository.PlayerWarStatsView;

@ExtendWith(MockitoExtension.class)
class ClanPlayerStatsServiceTest {

    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanWarParticipantRepository participantRepository;
    @Mock
    private ClanCosmeticService cosmeticService;

    @InjectMocks
    private ClanPlayerStatsService service;

    private final PlayerWarStatsView war = mock(PlayerWarStatsView.class);

    @BeforeEach
    void setUp() {
        when(participantRepository.findPlayerWarStats(7L)).thenReturn(war);
    }

    @Test
    void aMemberSeesTheirClanRankAndWarRecord() {
        Clan clan = Clan.builder().id(UUID.randomUUID()).name("Night Owls").tag("NOW").build();
        Instant joined = Instant.parse("2026-08-01T00:00:00Z");
        when(memberRepository.findOpenByUserId(7L)).thenReturn(Optional.of(
                ClanMember.builder().clan(clan).role(ClanRole.officer).joinedAt(joined).build()));
        PublicClanResponse ref = PublicClanResponse.of(clan, List.of());
        when(cosmeticService.publicRefs(List.of(clan))).thenReturn(Map.of(clan.getId(), ref));
        when(war.getWarsFought()).thenReturn(4L);
        when(war.getWarsWon()).thenReturn(3L);
        when(war.getBreaksDealt()).thenReturn(2L);
        when(war.getWarXp()).thenReturn(650.0);

        ClanStatsResponse stats = service.forUser(7L);

        assertThat(stats.clan()).isSameAs(ref);
        assertThat(stats.role()).isEqualTo(ClanRole.officer);
        assertThat(stats.joinedAt()).isEqualTo(joined);
        assertThat(stats.warsFought()).isEqualTo(4);
        assertThat(stats.warsWon()).isEqualTo(3);
        assertThat(stats.breaksDealt()).isEqualTo(2);
        assertThat(stats.warXp()).isEqualTo(650.0);
    }

    @Test
    void aPlayerOutOfAClanKeepsTheWarRecordTheyEarnedBefore() {
        when(memberRepository.findOpenByUserId(7L)).thenReturn(Optional.empty());
        when(war.getHits()).thenReturn(12L);

        ClanStatsResponse stats = service.forUser(7L);

        assertThat(stats.clan()).isNull();
        assertThat(stats.role()).isNull();
        assertThat(stats.hits()).isEqualTo(12);
        verify(cosmeticService, never()).publicRefs(any());
    }
}
