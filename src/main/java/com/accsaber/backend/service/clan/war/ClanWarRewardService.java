package com.accsaber.backend.service.clan.war;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.clan.ClanWarRewardItemRequest;
import com.accsaber.backend.model.dto.response.clan.ClanWarRewardItemResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;
import com.accsaber.backend.repository.clan.war.ClanWarRewardItemRepository;
import com.accsaber.backend.repository.item.ItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanWarRewardService {

    private final ClanWarRewardItemRepository rewardItemRepository;
    private final ItemRepository itemRepository;

    public List<ClanWarRewardItemResponse> list() {
        return rewardItemRepository.findAllWithItems().stream().map(ClanWarRewardItemResponse::of).toList();
    }

    @Transactional
    public ClanWarRewardItemResponse create(ClanWarRewardItemRequest request) {
        if (request.getItemId() == null) {
            throw new ValidationException("itemId", "is required");
        }
        ClanWarRewardItem reward = ClanWarRewardItem.builder()
                .item(itemRepository.findById(request.getItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Item", request.getItemId())))
                .build();
        return ClanWarRewardItemResponse.of(rewardItemRepository.save(apply(reward, request)));
    }

    @Transactional
    public ClanWarRewardItemResponse update(UUID rewardId, ClanWarRewardItemRequest request) {
        return ClanWarRewardItemResponse.of(rewardItemRepository.save(apply(find(rewardId), request)));
    }

    @Transactional
    public void deactivate(UUID rewardId) {
        find(rewardId).setActive(false);
    }

    private ClanWarRewardItem find(UUID rewardId) {
        return rewardItemRepository.findById(rewardId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanWarRewardItem", rewardId));
    }

    private static ClanWarRewardItem apply(ClanWarRewardItem reward, ClanWarRewardItemRequest request) {
        if (request.getQuantity() != null) {
            reward.setQuantity(request.getQuantity());
        }
        if (request.getTopContributors() != null) {
            reward.setTopContributors(request.getTopContributors());
        }
        if (request.getActive() != null) {
            reward.setActive(request.getActive());
        }
        return reward;
    }
}
