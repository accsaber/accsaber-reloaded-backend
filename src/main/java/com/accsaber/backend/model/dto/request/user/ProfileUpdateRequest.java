package com.accsaber.backend.model.dto.request.user;

import java.util.List;

import lombok.Data;

@Data
public class ProfileUpdateRequest {

    private String name;

    private String bio;

    private List<PinnedScoreEntry> pinnedScores;
}
