package com.accsaber.backend.model.dto.response.map;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoteListResponse {

    List<VoteResponse> votes;
    boolean reweightReady;
    boolean unrankReady;
}
