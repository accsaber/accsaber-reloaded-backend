package com.accsaber.backend.service.map;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.map.MapHubLookup;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.MapHubResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.chat.ChatService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapHubService {

    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapService mapService;
    private final MapVotingService mapVotingService;
    private final ChatService chatService;
    private final MapChatChannel mapChatChannel;
    private final MapSubscriptionService subscriptionService;

    public MapHubResponse<PublicMapDifficultyResponse> detail(UUID difficultyId, Long viewerId, Pageable comments) {
        return new MapHubResponse<>(mapService.getDifficultyResponsePublic(difficultyId),
                mapVotingService.getAnonymousRankVotes(difficultyId),
                chatService.getTranscript(mapChatChannel, difficultyId, comments),
                subscribed(difficultyId, viewerId));
    }

    public MapHubResponse<PublicMapDifficultyResponse> detailByCode(MapHubLookup lookup, Long viewerId,
            Pageable comments) {
        return detail(resolve(lookup), viewerId, comments);
    }

    public MapHubResponse<MapDifficultyResponse> staffDetail(UUID difficultyId, MapVoteAction voteType, Long viewerId,
            Pageable comments) {
        return new MapHubResponse<>(mapService.getDifficultyResponse(difficultyId),
                mapVotingService.getVotes(difficultyId, voteType),
                chatService.getTranscript(mapChatChannel, difficultyId, comments),
                subscribed(difficultyId, viewerId));
    }

    public MapHubResponse<MapDifficultyResponse> staffDetailByCode(MapHubLookup lookup, MapVoteAction voteType,
            Long viewerId, Pageable comments) {
        return staffDetail(resolve(lookup), voteType, viewerId, comments);
    }

    private UUID resolve(MapHubLookup lookup) {
        return mapDifficultyRepository.findHubDifficultyIds(lookup.beatsaverCode(), lookup.difficulty(),
                lookup.characteristic(), PageRequest.of(0, 1)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", lookup.beatsaverCode()));
    }

    private Boolean subscribed(UUID difficultyId, Long viewerId) {
        return viewerId == null ? null : subscriptionService.follows(difficultyId, viewerId);
    }
}
