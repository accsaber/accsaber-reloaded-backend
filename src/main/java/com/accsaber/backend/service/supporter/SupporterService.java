package com.accsaber.backend.service.supporter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.supporter.KofiClaimSource;
import com.accsaber.backend.model.entity.supporter.KofiEvent;
import com.accsaber.backend.model.entity.supporter.KofiEventType;
import com.accsaber.backend.model.entity.supporter.SupporterAccount;
import com.accsaber.backend.model.entity.supporter.SupporterTier;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.supporter.KofiEventRepository;
import com.accsaber.backend.repository.supporter.SupporterAccountRepository;
import com.accsaber.backend.repository.supporter.SupporterTierRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupporterService {

    private static final Duration BILLING_CYCLE = Duration.ofDays(30);
    private static final Duration ROLE_CORRELATION_WINDOW = Duration.ofMinutes(30);

    private final KofiEventRepository kofiEventRepository;
    private final SupporterAccountRepository supporterAccountRepository;
    private final SupporterTierRepository supporterTierRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final ItemService itemService;
    private final com.accsaber.backend.repository.user.OauthConnectionRepository oauthConnectionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public KofiEvent recordEvent(String rawJsonPayload) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawJsonPayload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed Ko-fi webhook payload", e);
        }

        String txnId = textOrNull(payload, "kofi_transaction_id");
        if (txnId == null || txnId.isBlank()) {
            throw new IllegalArgumentException("kofi_transaction_id missing");
        }

        Optional<KofiEvent> existing = kofiEventRepository.findByKofiTransactionId(txnId);
        if (existing.isPresent()) {
            log.info("Ko-fi event {} already recorded, ignoring duplicate", txnId);
            return existing.get();
        }

        KofiEvent event = KofiEvent.builder()
                .kofiTransactionId(txnId)
                .type(KofiEventType.fromKofiPayload(textOrNull(payload, "type")))
                .email(textOrNull(payload, "email"))
                .fromName(textOrNull(payload, "from_name"))
                .amountCents(parseAmountCents(textOrNull(payload, "amount")))
                .currency(textOrDefault(payload, "currency", "USD"))
                .tierName(textOrNull(payload, "tier_name"))
                .subscription(boolOrFalse(payload, "is_subscription_payment"))
                .firstSubscription(boolOrFalse(payload, "is_first_subscription_payment"))
                .payload(payload)
                .build();
        KofiEvent saved = kofiEventRepository.save(event);
        tryAutoClaimByEmail(saved);
        return saved;
    }

    private void tryAutoClaimByEmail(KofiEvent event) {
        if (event.getEmail() == null || event.getEmail().isBlank()) return;
        List<Long> prior = kofiEventRepository.findClaimedUserIdsByEmail(
                event.getEmail(), org.springframework.data.domain.PageRequest.of(0, 1));
        if (prior.isEmpty()) return;
        try {
            claimEventForUser(event.getId(), prior.get(0), KofiClaimSource.webhook_email_match);
        } catch (Exception e) {
            log.warn("Auto-claim by email for event {} failed: {}", event.getKofiTransactionId(), e.getMessage());
        }
    }

    @Transactional
    public void claimEventForUser(UUID eventId, Long userId, KofiClaimSource source) {
        KofiEvent event = kofiEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("KofiEvent", eventId));
        if (event.getClaimedUser() != null) {
            throw new ConflictException("Ko-fi event already claimed", event.getKofiTransactionId());
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        event.setClaimedUser(user);
        event.setClaimedAt(Instant.now());
        event.setClaimSource(source);
        kofiEventRepository.save(event);

        applyEntitlement(userId, event.getAmountCents(), event.getTierName());
        log.info("Ko-fi event {} claimed by user {} via {}", event.getKofiTransactionId(), userId, source);
    }

    @Transactional
    public Optional<KofiEvent> claimByRoleSignal(Long userId, String tierName, Instant assignedAt) {
        Instant since = assignedAt.minus(ROLE_CORRELATION_WINDOW);
        List<KofiEvent> candidates = kofiEventRepository.findUnclaimedByTierSince(tierName, since);
        if (candidates.isEmpty()) {
            log.info("Role-signal claim for user {} tier {}: no unclaimed event in window", userId, tierName);
            return Optional.empty();
        }
        KofiEvent event = candidates.get(0);
        claimEventForUser(event.getId(), userId, KofiClaimSource.bot_role_event);
        return Optional.of(event);
    }

    @Transactional
    public void claimByAdmin(String kofiTransactionId, Long userId) {
        KofiEvent event = kofiEventRepository.findByKofiTransactionId(kofiTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("KofiEvent", kofiTransactionId));
        claimEventForUser(event.getId(), userId, KofiClaimSource.admin_assign);
    }

    @Transactional
    public Optional<KofiEvent> claimByRoleSignalForDiscord(String discordId, String tierName, Instant assignedAt) {
        Long userId = resolveDiscordToUserId(discordId);
        return claimByRoleSignal(userId, tierName, assignedAt);
    }

    @Transactional
    public void claimByAdminForDiscord(String kofiTransactionId, String discordId) {
        Long userId = resolveDiscordToUserId(discordId);
        claimByAdmin(kofiTransactionId, userId);
    }

    @Transactional(readOnly = true)
    public SupporterAccount findAccount(Long userId) {
        return supporterAccountRepository.findById(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SupporterAccount> findCredits(
            String status, org.springframework.data.domain.Pageable pageable) {
        String normalized = status == null ? "all" : status.toLowerCase();
        if (!normalized.equals("all") && !normalized.equals("active") && !normalized.equals("past")) {
            normalized = "all";
        }
        return supporterAccountRepository.findCredits(normalized, pageable);
    }

    private Long resolveDiscordToUserId(String discordId) {
        return oauthConnectionRepository.findByProviderAndProviderUserIdAndActiveTrue("discord", discordId)
                .map(c -> c.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("DiscordLink", discordId));
    }

    @Transactional
    public void applyEntitlement(Long userId, int amountCents, String webhookTierName) {
        SupporterAccount account = loadOrCreateAccount(userId);
        account.setLifetimeSupportedCents(account.getLifetimeSupportedCents() + amountCents);

        SupporterTier explicitTier = resolveTierByName(webhookTierName).orElse(null);
        int remaining = amountCents + account.getBalanceCents();

        if (account.getCurrentTier() == null) {
            SupporterTier chosenTier = explicitTier != null
                    ? explicitTier
                    : highestAffordableTier(remaining).orElse(null);
            if (chosenTier == null) {
                account.setBalanceCents(remaining);
                supporterAccountRepository.save(account);
                return;
            }
            startTier(account, chosenTier, remaining);
        } else if (explicitTier != null && isHigherTier(explicitTier, account.getCurrentTier())) {
            int proratedRefund = prorateRemaining(account);
            remaining += proratedRefund;
            startTier(account, explicitTier, remaining);
        } else {
            account.setBalanceCents(remaining);
        }

        supporterAccountRepository.save(account);
        grantTierItems(userId, account.getCurrentTier().getTierKey());
    }

    @Scheduled(cron = "${accsaber.scheduler.supporter-debit-cron:0 30 4 * * *}")
    @Transactional
    public void dailyDebit() {
        Instant cutoff = Instant.now().minus(BILLING_CYCLE);
        List<SupporterAccount> due = supporterAccountRepository.findActiveTiersDueForDebit(cutoff);
        log.info("Supporter debit: {} accounts due", due.size());
        for (SupporterAccount account : due) {
            try {
                debitOne(account);
            } catch (Exception e) {
                log.error("Failed to debit supporter account {}: {}", account.getUserId(), e.getMessage());
            }
        }
    }

    private void debitOne(SupporterAccount account) {
        int cost = account.getCurrentTier().getMonthlyCostCents();
        int balance = account.getBalanceCents();
        if (balance >= cost) {
            account.setBalanceCents(balance - cost);
            account.setLastDebitAt(Instant.now());
        } else {
            log.info("Supporter account {} balance {}c < tier cost {}c, revoking tier {}",
                    account.getUserId(), balance, cost, account.getCurrentTier().getTierKey());
            account.setCurrentTier(null);
            account.setTierStartedAt(null);
            account.setLastDebitAt(null);
        }
        supporterAccountRepository.save(account);
    }

    private void startTier(SupporterAccount account, SupporterTier tier, int availableCents) {
        int cost = tier.getMonthlyCostCents();
        Instant now = Instant.now();
        account.setCurrentTier(tier);
        account.setTierStartedAt(now);
        account.setLastDebitAt(now);
        account.setBalanceCents(Math.max(0, availableCents - cost));
    }

    private int prorateRemaining(SupporterAccount account) {
        if (account.getLastDebitAt() == null || account.getCurrentTier() == null) {
            return 0;
        }
        Duration elapsed = Duration.between(account.getLastDebitAt(), Instant.now());
        if (elapsed.compareTo(BILLING_CYCLE) >= 0) return 0;
        BigDecimal cost = BigDecimal.valueOf(account.getCurrentTier().getMonthlyCostCents());
        BigDecimal unused = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(elapsed.toMillis()).divide(
                        BigDecimal.valueOf(BILLING_CYCLE.toMillis()), 6, RoundingMode.HALF_UP));
        return cost.multiply(unused).setScale(0, RoundingMode.DOWN).intValue();
    }

    private SupporterAccount loadOrCreateAccount(Long userId) {
        return supporterAccountRepository.findById(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
                    return supporterAccountRepository.save(SupporterAccount.builder()
                            .user(user)
                            .build());
                });
    }

    private Optional<SupporterTier> resolveTierByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return supporterTierRepository.findByDisplayNameIgnoreCase(name.trim());
    }

    private Optional<SupporterTier> highestAffordableTier(int cents) {
        return supporterTierRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(t -> t.getMonthlyCostCents() <= cents)
                .reduce((a, b) -> a.getSortOrder() >= b.getSortOrder() ? a : b);
    }

    private boolean isHigherTier(SupporterTier candidate, SupporterTier current) {
        return candidate.getSortOrder() > current.getSortOrder();
    }

    private void grantTierItems(Long userId, String tierKey) {
        Set<TierItemRef> refs = new HashSet<>();
        refs.add(new TierItemRef("profile_border_shape", "Supporter Frame"));
        if (rankOf(tierKey) >= 1) {
            refs.add(new TierItemRef("profile_border_color", "Bronze Supporter Color"));
            refs.add(new TierItemRef("title", "Bronze Helper"));
        }
        if (rankOf(tierKey) >= 2) {
            refs.add(new TierItemRef("profile_border_color", "Silver Supporter Color"));
            refs.add(new TierItemRef("title", "Silver Hero"));
        }
        if (rankOf(tierKey) >= 3) {
            refs.add(new TierItemRef("profile_border_color", "Golden Supporter Color"));
            refs.add(new TierItemRef("title", "Golden Legend"));
        }
        for (TierItemRef ref : refs) {
            Optional<Item> item = itemRepository.findByType_KeyAndNameAndActiveTrue(ref.typeKey, ref.name);
            if (item.isEmpty()) {
                log.warn("Supporter item missing: {} / {}", ref.typeKey, ref.name);
                continue;
            }
            itemService.awardSystem(userId, item.get().getId(), ItemSource.supporter_tier, tierKey,
                    "Ko-fi supporter (" + tierKey + ")");
        }
    }

    private int rankOf(String tierKey) {
        return switch (tierKey) {
            case "bronze" -> 1;
            case "silver" -> 2;
            case "gold" -> 3;
            default -> 0;
        };
    }

    private record TierItemRef(String typeKey, String name) {}

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String textOrDefault(JsonNode node, String field, String defaultValue) {
        String v = textOrNull(node, field);
        return v == null ? defaultValue : v;
    }

    private static boolean boolOrFalse(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() && v.asBoolean(false);
    }

    private static int parseAmountCents(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        return new BigDecimal(raw).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
