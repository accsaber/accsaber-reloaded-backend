package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.repository.clan.war.ClanWarRepository;

@ExtendWith(MockitoExtension.class)
class ClanWarScoreGateTest {

    @Mock
    private ClanWarRepository warRepository;

    @InjectMocks
    private ClanWarScoreGate gate;

    @Test
    void onlyAFighterOnAPoolMapGetsThrough() {
        UUID pooled = UUID.randomUUID();
        when(warRepository.findActivePoolDifficultyIds()).thenReturn(List.of(pooled));
        when(warRepository.findActiveParticipantIds()).thenReturn(List.of(7L));

        gate.refreshAfterCommit();

        assertThat(gate.mayMatter(7L, pooled)).isTrue();
        assertThat(gate.mayMatter(8L, pooled)).isFalse();
        assertThat(gate.mayMatter(7L, UUID.randomUUID())).isFalse();
    }
}
