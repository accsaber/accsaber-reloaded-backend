package com.accsaber.backend.model.dto.response.clan;

import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.score.MyScoreSummary;
import com.accsaber.backend.model.entity.clan.war.ClanWarPoolSource;

public record ClanWarPoolEntryResponse(PublicMapDifficultyResponse difficulty, PublicClanResponse pickedBy,
        ClanWarPoolSource source, MyScoreSummary viewerScore) {
}
