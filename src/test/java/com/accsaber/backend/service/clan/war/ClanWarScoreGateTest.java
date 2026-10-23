package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.service.infra.ModifierCacheService;

@ExtendWith(MockitoExtension.class)
class ClanWarScoreGateTest {

    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ModifierCacheService modifierCacheService;

    @InjectMocks
    private ClanWarScoreGate gate;

    @Test
    void onlyAFighterOnAPoolMapWithoutASpeedUpGetsThrough() {
        UUID pooled = UUID.randomUUID();
        UUID fasterSong = UUID.randomUUID();
        when(warRepository.findActivePoolDifficultyIds()).thenReturn(List.of(pooled));
        when(warRepository.findActiveParticipantIds()).thenReturn(List.of(7L));
        when(modifierCacheService.containsSpeedUp(anyList())).thenAnswer(inv -> inv.<List<UUID>>getArgument(0).contains(fasterSong));

        gate.refreshAfterCommit();

        assertThat(gate.mayMatter(play(7L, pooled))).isTrue();
        assertThat(gate.mayMatter(play(8L, pooled))).isFalse();
        assertThat(gate.mayMatter(play(7L, UUID.randomUUID()))).isFalse();
        assertThat(gate.mayMatter(play(7L, pooled, fasterSong))).isFalse();
    }

    private static ScoreResponse play(Long userId, UUID difficultyId, UUID... modifierIds) {
        return ScoreResponse.builder().userId(userId.toString()).mapDifficultyId(difficultyId)
                .modifierIds(List.of(modifierIds)).build();
    }
}
