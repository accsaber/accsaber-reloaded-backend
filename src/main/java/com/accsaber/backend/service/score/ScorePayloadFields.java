package com.accsaber.backend.service.score;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.entity.score.Score;

final class ScorePayloadFields {

    private ScorePayloadFields() {
    }

    private record Binding<T>(
            Function<SubmitScoreRequest, T> fromRequest,
            Function<Score, T> fromScore,
            BiConsumer<Score, T> setOnScore) {
    }

    private static final List<Binding<?>> BINDINGS = List.of(
            new Binding<>(SubmitScoreRequest::getBlScoreId, Score::getBlScoreId, Score::setBlScoreId),
            new Binding<>(SubmitScoreRequest::getMaxCombo, Score::getMaxCombo, Score::setMaxCombo),
            new Binding<>(SubmitScoreRequest::getBadCuts, Score::getBadCuts, Score::setBadCuts),
            new Binding<>(SubmitScoreRequest::getMisses, Score::getMisses, Score::setMisses),
            new Binding<>(SubmitScoreRequest::getWallHits, Score::getWallHits, Score::setWallHits),
            new Binding<>(SubmitScoreRequest::getBombHits, Score::getBombHits, Score::setBombHits),
            new Binding<>(SubmitScoreRequest::getPauses, Score::getPauses, Score::setPauses),
            new Binding<>(SubmitScoreRequest::getStreak115, Score::getStreak115, Score::setStreak115),
            new Binding<>(SubmitScoreRequest::getPlayCount, Score::getPlayCount, Score::setPlayCount),
            new Binding<>(SubmitScoreRequest::getHmd, Score::getHmd, Score::setHmd),
            new Binding<>(SubmitScoreRequest::getTimeSet, Score::getTimeSet, Score::setTimeSet));

    static void applyAll(Score target, SubmitScoreRequest source) {
        for (Binding<?> b : BINDINGS) {
            applyOne(b, target, source);
        }
    }

    static boolean mergeNullOnly(Score target, SubmitScoreRequest source) {
        boolean changed = false;
        for (Binding<?> b : BINDINGS) {
            changed |= mergeOne(b, target, source);
        }
        return changed;
    }

    static void copyAll(Score from, Score to) {
        for (Binding<?> b : BINDINGS) {
            copyOne(b, from, to);
        }
    }

    private static <T> void applyOne(Binding<T> b, Score target, SubmitScoreRequest source) {
        b.setOnScore().accept(target, b.fromRequest().apply(source));
    }

    private static <T> boolean mergeOne(Binding<T> b, Score target, SubmitScoreRequest source) {
        if (b.fromScore().apply(target) != null) {
            return false;
        }
        T incoming = b.fromRequest().apply(source);
        if (incoming == null) {
            return false;
        }
        b.setOnScore().accept(target, incoming);
        return true;
    }

    private static <T> void copyOne(Binding<T> b, Score from, Score to) {
        b.setOnScore().accept(to, b.fromScore().apply(from));
    }
}
