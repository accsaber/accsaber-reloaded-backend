package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanXpGrantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanPlayXpService {

    private final ClanMemberRepository memberRepository;
    private final ClanXpGrantRepository grantRepository;
    private final ClanLevelService levelService;
    private final ClanProperties clanProperties;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScoreSubmitted(ScoreSubmittedEvent event) {
        ScoreResponse score = event.score();
        if (score.isPartial() || score.getXpGained() == null || score.getXpGained() <= 0) {
            return;
        }
        Instant setAt = score.getTimeSet() != null ? score.getTimeSet() : Instant.now();
        memberRepository.findOpenByUserId(Long.valueOf(score.getUserId()))
                .filter(member -> !member.getJoinedAt().isAfter(setAt))
                .ifPresent(member -> grant(member.getClan().getId(), score.getId()));
    }

    public int grantMissed(Instant from, Instant to) {
        int granted = 0;
        for (ClanXpGrantRepository.UngrantedPlayView play : grantRepository.findUngrantedPlays(from, to)) {
            if (grant(play.getClanId(), play.getScoreId())) {
                granted++;
            }
        }
        return granted;
    }

    private boolean grant(UUID clanId, UUID scoreId) {
        try {
            return levelService.grantXp(clanId, new ClanXpAward(clanProperties.getPlayClanXp(), ClanXpSource.play,
                    scoreId.toString(), true));
        } catch (RuntimeException e) {
            log.warn("Play XP for clan {} from score {} failed: {}", clanId, scoreId, e.getMessage());
            return false;
        }
    }
}
