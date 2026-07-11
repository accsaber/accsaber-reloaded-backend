package com.accsaber.backend.service.item;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.CrateContent;
import com.accsaber.backend.model.entity.item.CrateContent.CrateContentId;
import com.accsaber.backend.model.entity.item.CrateModifier;
import com.accsaber.backend.model.entity.item.CrateModifier.CrateModifierId;
import com.accsaber.backend.model.entity.item.CrateUnusualEffect;
import com.accsaber.backend.model.entity.item.CrateUnusualEffect.CrateUnusualEffectId;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.UnusualEffect;
import com.accsaber.backend.model.entity.item.UserCrateOpen;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.repository.item.CrateContentRepository;
import com.accsaber.backend.repository.item.CrateModifierRepository;
import com.accsaber.backend.repository.item.CrateUnusualEffectRepository;
import com.accsaber.backend.repository.item.ItemModifierRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UnusualEffectRepository;
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
    private final CrateModifierRepository crateModifierRepository;
    private final CrateUnusualEffectRepository crateUnusualEffectRepository;
    private final UserCrateOpenRepository userCrateOpenRepository;
    private final ItemRepository itemRepository;
    private final ItemModifierRepository itemModifierRepository;
    private final UnusualEffectRepository unusualEffectRepository;
    private final ItemService itemService;
    private final ModifierResolver modifierResolver;
    private final DuplicateUserService duplicateUserService;

    private final SecureRandom secureRandom = new SecureRandom();

    public List<Item> listCrates() {
        return itemRepository.findByType_Key(CRATE_TYPE_KEY);
    }

    public List<CrateContent> listContents(UUID crateItemId) {
        loadCrateItem(crateItemId);
        return crateContentRepository.findByCrateItem_Id(crateItemId);
    }

    public List<CrateContent> listVisibleContents(UUID crateItemId) {
        loadCrateItem(crateItemId);
        return crateContentRepository.findByCrateItem_IdAndRewardItem_VisibleTrue(crateItemId);
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

    public List<CrateModifier> listModifiers(UUID crateItemId) {
        loadCrateItem(crateItemId);
        return crateModifierRepository.findByCrateItem_Id(crateItemId);
    }

    @Transactional
    public CrateModifier upsertModifier(UUID crateItemId, UUID modifierId, BigDecimal dropChance) {
        if (dropChance == null || dropChance.signum() <= 0 || dropChance.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationException("dropChance", "must be between 0 (exclusive) and 1 (inclusive)");
        }
        Item crate = loadCrateItem(crateItemId);
        ItemModifier modifier = itemModifierRepository.findById(modifierId)
                .orElseThrow(() -> new ResourceNotFoundException("ItemModifier", modifierId));
        if (!modifier.isActive()) {
            throw new ValidationException("modifierId", "cannot attach an inactive modifier");
        }
        if (ItemModifier.NORMAL.equals(modifier.getKey())) {
            throw new ValidationException("modifierId", "the normal modifier cannot be attached to a crate");
        }

        CrateModifierId pk = CrateModifierId.builder()
                .crateItemId(crateItemId)
                .modifierId(modifierId)
                .build();

        CrateModifier attachment = crateModifierRepository.findById(pk).orElse(null);
        if (attachment == null) {
            attachment = CrateModifier.builder()
                    .id(pk)
                    .crateItem(crate)
                    .modifier(modifier)
                    .dropChance(dropChance)
                    .build();
        } else {
            attachment.setDropChance(dropChance);
        }
        return crateModifierRepository.save(attachment);
    }

    @Transactional
    public void removeModifier(UUID crateItemId, UUID modifierId) {
        loadCrateItem(crateItemId);
        CrateModifierId pk = CrateModifierId.builder()
                .crateItemId(crateItemId)
                .modifierId(modifierId)
                .build();
        if (!crateModifierRepository.existsById(pk)) {
            throw new ResourceNotFoundException("CrateModifier", modifierId);
        }
        crateModifierRepository.deleteById(pk);
    }

    public List<UnusualEffect> listUnusualEffects(UUID crateItemId) {
        loadCrateItem(crateItemId);
        return sortedEffects(crateUnusualEffectRepository.findByCrateItem_Id(crateItemId));
    }

    @Transactional
    public UnusualEffect attachUnusualEffect(UUID crateItemId, UUID effectId) {
        Item crate = loadCrateItem(crateItemId);
        UnusualEffect effect = unusualEffectRepository.findById(effectId)
                .orElseThrow(() -> new ResourceNotFoundException("UnusualEffect", effectId));
        if (!effect.isActive()) {
            throw new ValidationException("effectId", "cannot attach an inactive unusual effect");
        }
        CrateUnusualEffectId pk = CrateUnusualEffectId.builder()
                .crateItemId(crateItemId)
                .effectId(effectId)
                .build();
        if (!crateUnusualEffectRepository.existsById(pk)) {
            crateUnusualEffectRepository.save(CrateUnusualEffect.builder()
                    .id(pk)
                    .crateItem(crate)
                    .effect(effect)
                    .build());
        }
        return effect;
    }

    @Transactional
    public void detachUnusualEffect(UUID crateItemId, UUID effectId) {
        loadCrateItem(crateItemId);
        CrateUnusualEffectId pk = CrateUnusualEffectId.builder()
                .crateItemId(crateItemId)
                .effectId(effectId)
                .build();
        if (!crateUnusualEffectRepository.existsById(pk)) {
            throw new ResourceNotFoundException("CrateUnusualEffect", effectId);
        }
        crateUnusualEffectRepository.deleteById(pk);
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

        List<CrateContent> contents = crateContentRepository
                .findByCrateItem_IdAndRewardItem_VisibleTrue(crateItem.getId());
        if (contents.isEmpty()) {
            throw new ValidationException("crate", "crate has no contents configured");
        }

        long seed = secureRandom.nextLong();
        SplittableRandom rng = new SplittableRandom(seed);
        CrateContent winner = roll(contents, rng);
        Set<ItemModifier> rolledModifiers = rollModifiers(crateItem.getId(), rng);
        UnusualEffect unusualEffect = rollUnusualEffect(crateItem.getId(), rolledModifiers, rng);

        UUID consumedLinkId = crateLink.getId();
        String typeKey = crateItem.getType().getKey();

        userItemLinkRepository.delete(crateLink);
        userItemLinkRepository.flush();
        itemService.clearEquippedIfLinkGone(resolved, consumedLinkId, typeKey);

        UserItemLink rewardLink = itemService.awardFromCrate(resolved, winner.getRewardItem(),
                consumedLinkId, rolledModifiers, unusualEffect);

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

    private Set<ItemModifier> rollModifiers(UUID crateItemId, SplittableRandom rng) {
        List<CrateModifier> attached = crateModifierRepository.findByCrateItem_Id(crateItemId);
        Set<UUID> attachedIds = attached.stream()
                .map(cm -> cm.getModifier().getId())
                .collect(Collectors.toSet());
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<ItemModifier> globalCandidates = itemModifierRepository
                .findByActiveTrueAndGlobalDropChanceIsNotNull().stream()
                .filter(m -> !attachedIds.contains(m.getId()))
                .filter(m -> modifierResolver.inSeason(m, today))
                .toList();
        return ModifierResolver.rollModifiers(attached, globalCandidates, rng);
    }

    private UnusualEffect rollUnusualEffect(UUID crateItemId, Set<ItemModifier> rolledModifiers, RandomGenerator rng) {
        boolean unusualRolled = rolledModifiers.stream()
                .anyMatch(m -> ItemModifier.UNUSUAL.equals(m.getKey()));
        if (!unusualRolled) {
            return null;
        }
        return pickUnusualEffect(crateUnusualEffectRepository.findByCrateItem_Id(crateItemId), rng);
    }

    static UnusualEffect pickUnusualEffect(List<CrateUnusualEffect> attached, RandomGenerator rng) {
        if (attached.isEmpty()) {
            return null;
        }
        List<UnusualEffect> effects = sortedEffects(attached);
        return effects.get(rng.nextInt(effects.size()));
    }

    private static List<UnusualEffect> sortedEffects(List<CrateUnusualEffect> attached) {
        return attached.stream()
                .map(CrateUnusualEffect::getEffect)
                .sorted(Comparator.comparing(UnusualEffect::getKey))
                .toList();
    }

    static CrateContent roll(List<CrateContent> contents, SplittableRandom rng) {
        long total = 0L;
        for (CrateContent c : contents) {
            total += c.getDropWeight();
        }
        long pick = rng.nextLong(total);
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
