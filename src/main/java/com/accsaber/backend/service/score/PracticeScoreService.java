package com.accsaber.backend.service.score;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.score.PracticeScoreRequest;
import com.accsaber.backend.model.dto.response.PracticeScoreResponse;
import com.accsaber.backend.model.entity.score.PracticeScore;
import com.accsaber.backend.repository.score.PracticeScoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeScoreService {

    private static final int MAX_BATCH = 20;
    private static final int MAX_NAME_LENGTH = 24;
    private static final int MAX_SCORE = 1_000_000;
    private static final int MAX_LEVEL = 999;
    private static final int MAX_PAGE_SIZE = 100;

    private final PracticeScoreRepository practiceScoreRepository;

    @Transactional
    public void submit(List<PracticeScoreRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ValidationException("entries", "must not be empty");
        }
        if (requests.size() > MAX_BATCH) {
            throw new ValidationException("entries", "at most " + MAX_BATCH + " per submission");
        }
        requests.forEach(this::insert);
    }

    public List<PracticeScoreResponse> top(int size) {
        int capped = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return practiceScoreRepository.findAllByOrderByScoreDesc(PageRequest.of(0, capped))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void insert(PracticeScoreRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("name", "must be 1-" + MAX_NAME_LENGTH + " characters");
        }
        if (request.score() < 0 || request.score() > MAX_SCORE) {
            throw new ValidationException("score", "out of range");
        }
        if (request.level() < 1 || request.level() > MAX_LEVEL) {
            throw new ValidationException("level", "out of range");
        }
        if (request.accuracy() < 0 || request.accuracy() > 100 || !Double.isFinite(request.accuracy())) {
            throw new ValidationException("accuracy", "out of range");
        }
        if (request.badCuts() < 0 || request.badCuts() > MAX_SCORE) {
            throw new ValidationException("badCuts", "out of range");
        }
        if (request.bombHits() < 0 || request.bombHits() > MAX_SCORE) {
            throw new ValidationException("bombHits", "out of range");
        }
        if (request.playedAt() == null) {
            throw new ValidationException("playedAt", "required");
        }
        UUID id = request.id() == null ? UUID.randomUUID() : request.id();
        practiceScoreRepository.insertIfAbsent(
                id,
                name,
                request.score(),
                request.level(),
                request.accuracy(),
                request.badCuts(),
                request.bombHits(),
                request.playedAt());
    }

    private PracticeScoreResponse toResponse(PracticeScore entity) {
        return PracticeScoreResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .score(entity.getScore())
                .level(entity.getLevel())
                .accuracy(entity.getAccuracy())
                .badCuts(entity.getBadCuts())
                .bombHits(entity.getBombHits())
                .playedAt(entity.getPlayedAt())
                .build();
    }
}
