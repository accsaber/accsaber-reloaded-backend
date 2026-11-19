package com.accsaber.backend.model.event;

import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.VoteType;

public record MapVoteCastEvent(MapDifficulty difficulty, VoteType vote, boolean updated) {
}
