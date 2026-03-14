package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.websocket.server.ScoreFeedWebSocketHandler;

@ExtendWith(MockitoExtension.class)
class ScoreBroadcastServiceTest {

        @Mock
        private ScoreFeedWebSocketHandler scoreFeedHandler;

        @InjectMocks
        private ScoreBroadcastService scoreBroadcastService;

        private ScoreResponse buildScoreResponse() {
                return ScoreResponse.builder()
                                .id(UUID.randomUUID())
                                .userId(76561198000000001L)
                                .mapDifficultyId(UUID.randomUUID())
                                .score(950_000)
                                .ap(new BigDecimal("500.000000"))
                                .weightedAp(new BigDecimal("500.000000"))
                                .modifierIds(Collections.emptyList())
                                .build();
        }

        @Test
        void onScoreSubmitted_serializesAndBroadcasts() {
                ScoreResponse score = buildScoreResponse();

                scoreBroadcastService.onScoreSubmitted(new ScoreSubmittedEvent(score));

                verify(scoreFeedHandler).broadcast(argThat(json -> {
                        assertThat(json).contains("\"userId\":" + score.getUserId());
                        assertThat(json).contains("\"score\":950000");
                        return true;
                }));
        }
}
