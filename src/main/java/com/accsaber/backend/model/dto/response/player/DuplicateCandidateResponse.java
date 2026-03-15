package com.accsaber.backend.model.dto.response.player;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DuplicateCandidateResponse {

    String primaryUserId;
    String primaryUserName;
    String secondaryUserId;
    String secondaryUserName;
    String country;
    int sharedDifficulties;
}
