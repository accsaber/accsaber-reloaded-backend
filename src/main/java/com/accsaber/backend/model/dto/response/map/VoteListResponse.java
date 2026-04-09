package com.accsaber.backend.model.dto.response.map;

import java.util.List;

import com.accsaber.backend.model.entity.map.VoteType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoteListResponse {

    List<VoteResponse> votes;
    boolean rankReady;
    boolean reweightReady;
    boolean unrankReady;
    int criteriaUpvotes;
    int criteriaDownvotes;
    VoteType headCriteriaVote;
}
