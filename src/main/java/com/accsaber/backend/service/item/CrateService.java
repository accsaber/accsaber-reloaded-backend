package com.accsaber.backend.service.item;

import java.security.SecureRandom;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.CrateContent;
import com.accsaber.backend.model.entity.item.CrateContent.CrateContentId;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.UserCrateOpen;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.repository.item.CrateContentRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UserCrateOpenRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrateService {

    private static final String CRATE_TYPE_KEY = "crate";

    private final UserItemLinkRepository userItemLinkRepository;
    private final CrateContentRepository crateContentRepository;
    private final UserCrateOpenRepository userCrateOpenRepository;
    private final ItemRepository itemRepository;
    private final ItemService itemService;
    private final DuplicateUserService duplicateUserService;

    private final SecureRandom secureRandom = new SecureRandom();

    public List<Item> listCrates() {
        return itemRepository.findByType_Key(CRATE_TYPE_KEY);
    }

    public List<CrateContent> listContents(UUID crateItemId) {
        loadCrateItem(crateItemId);
        return crateContentRepository.findByCrateItem_Id(crateItemId);
    }

    @Transactional
    public CrateContent upsertContent(UUID crateItemId, UUID rewardItemId, int dropWeight) {
        if (dropWeight < 1) {
            throw new ValidationException("dropWeight", "must be at least 1");
        }
        if (crateItemId.equals(rewardItemId)) {
            throw new ValidationException("rewardItemId", "a crate cannot drop itself");
        }
        Item crate = loadCrateItem(crateItemId);
        Item reward = itemRepository.findByIdAndActiveTrue(rewardItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", rewardItemId));
        if (reward.isDeprecated()) {
            throw new ValidationException("rewardItemId", "cannot add a deprecated item as a reward");
        }

        CrateContentId pk = CrateContentId.builder()
                .crateItemId(crateItemId)
                .rewardItemId(rewardItemId)
                .build();

        CrateContent content = crateContentRepository.findById(pk).orElse(null);
        if (content == null) {
            content = CrateContent.builder()
                    .id(pk)
                    .crateItem(crate)
                    .rewardItem(reward)
                    .dropWeight(dropWeight)
                    .build();
        } else {
            content.setDropWeight(dropWeight);
        }
        return crateContentRepository.save(content);
    }

    @Transactional
    public void removeContent(UUID crateItemId, UUID rewardItemId) {
        loadCrateItem(crateItemId);
        CrateContentId pk = CrateContentId.builder()
                .crateItemId(crateItemId)
                .rewardItemId(rewardItemId)
                .build();
        if (!crateContentRepository.existsById(pk)) {
            throw new ResourceNotFoundException("CrateContent", rewardItemId);
        }
        crateContentRepository.deleteById(pk);
    }

    private Item loadCrateItem(UUID crateItemId) {
        Item crate = itemRepository.findById(crateItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", crateItemId));
        if (!CRATE_TYPE_KEY.equals(crate.getType().getKey())) {
            throw new ValidationException("crateItemId", "item is not a crate");
        }
        return crate;
    }

    @Transactional
    public UserCrateOpen openCrate(Long userId, UUID userItemLinkId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);

        UserItemLink crateLink = userItemLinkRepository.findByIdForUpdate(userItemLinkId)
                .orElseThrow(() -> new ResourceNotFoundException("UserItemLink", userItemLinkId));

        if (!crateLink.getUser().getId().equals(resolved)) {
            throw new ValidationException("linkId", "user does not own this item link");
        }

        Item crateItem = crateLink.getItem();
        if (!CRATE_TYPE_KEY.equals(crateItem.getType().getKey())) {
            throw new ValidationException("linkId", "item is not a crate");
        }

        List<CrateContent> contents = crateContentRepository.findByCrateItem_Id(crateItem.getId());
        if (contents.isEmpty()) {
            throw new ValidationException("crate", "crate has no contents configured");
        }

        long seed = secureRandom.nextLong();
        CrateContent winner = roll(contents, seed);

        UUID consumedLinkId = crateLink.getId();
        String typeKey = crateItem.getType().getKey();

        userItemLinkRepository.delete(crateLink);
        userItemLinkRepository.flush();
        itemService.clearEquippedIfLinkGone(resolved, consumedLinkId, typeKey);

        UserItemLink rewardLink = itemService.awardFromCrate(resolved, winner.getRewardItem(), consumedLinkId);

        UserCrateOpen open = UserCrateOpen.builder()
                .user(rewardLink.getUser())
                .crateItem(crateItem)
                .consumedLinkId(consumedLinkId)
                .rewardLink(rewardLink)
                .rewardItem(winner.getRewardItem())
                .rollSeed(seed)
                .build();
        return userCrateOpenRepository.save(open);
    }

    static CrateContent roll(List<CrateContent> contents, long seed) {
        long total = 0L;
        for (CrateContent c : contents) {
            total += c.getDropWeight();
        }
        long pick = new SplittableRandom(seed).nextLong(total);
        long acc = 0L;
        for (CrateContent c : contents) {
            acc += c.getDropWeight();
            if (pick < acc) {
                return c;
            }
        }
        return contents.get(contents.size() - 1);
    }
}
