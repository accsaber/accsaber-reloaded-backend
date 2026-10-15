package com.accsaber.backend.service.stats;

import static com.accsaber.backend.service.stats.StatsQueryRunner.userIdentity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.item.ItemHolderSort;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.statistics.BiggestTraderResponse;
import com.accsaber.backend.model.dto.response.statistics.CollectionCompletionResponse;
import com.accsaber.backend.model.dto.response.statistics.EssenceEarnedResponse;
import com.accsaber.backend.model.dto.response.statistics.FirstEditionHolderResponse;
import com.accsaber.backend.model.dto.response.statistics.FirstEditionsResponse;
import com.accsaber.backend.model.dto.response.statistics.InventoryValueResponse;
import com.accsaber.backend.model.dto.response.statistics.ItemHolderResponse;
import com.accsaber.backend.model.dto.response.statistics.ItemScarcityResponse;
import com.accsaber.backend.model.dto.response.statistics.MostCratesOpenedResponse;
import com.accsaber.backend.model.dto.response.statistics.MostItemsResponse;
import com.accsaber.backend.model.dto.response.statistics.RarestUnboxedResponse;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.service.item.ItemMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemStatisticsService {

    private final StatsQueryRunner queryRunner;
    private final ItemRepository itemRepository;

    @Cacheable(value = "statistics", key = "'mostitems:' + #type + ':' + #modifier + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<MostItemsResponse> getMostItems(String type, String modifier, String country, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.mostItems(type, modifier, country, params);
        return queryRunner.paged(sql, params, pageable, row -> MostItemsResponse.builder()
                .user(userIdentity(row, 0))
                .itemCount(((Number) row[5]).longValue())
                .build());
    }

    @Cacheable(value = "statistics", key = "'mostcrates:' + #crateId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<MostCratesOpenedResponse> getMostCratesOpened(UUID crateId, String country, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.mostCratesOpened(crateId, country, params);
        return queryRunner.paged(sql, params, pageable, row -> MostCratesOpenedResponse.builder()
                .user(userIdentity(row, 0))
                .crateCount(((Number) row[5]).longValue())
                .build());
    }

    @Cacheable(value = "statistics", key = "'rarestunboxed:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<RarestUnboxedResponse> getRarestUnboxed(String country, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.rarestUnboxed(country, params);
        return withItems(queryRunner.paged(sql, params, pageable, row -> RarestUnboxedResponse.builder()
                .linkId((UUID) row[0])
                .user(userIdentity(row, 1))
                .itemId((UUID) row[6])
                .serialNumber(row[11] != null ? ((Number) row[11]).longValue() : null)
                .modifierCount(((Number) row[12]).longValue())
                .modifiers(splitKeys((String) row[13]))
                .unusualEffect((String) row[14])
                .build()),
                RarestUnboxedResponse::getItemId, (row, item) -> row.toBuilder().item(item).build());
    }

    @Cacheable(value = "statistics", key = "'valuableinventory:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<InventoryValueResponse> getMostValuableInventory(String country, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.mostValuableInventory(country, params);
        return queryRunner.paged(sql, params, pageable, row -> InventoryValueResponse.builder()
                .user(userIdentity(row, 0))
                .itemsValue(((Number) row[5]).longValue())
                .essenceBalance(((Number) row[6]).longValue())
                .totalValue(((Number) row[7]).longValue())
                .build());
    }

    @Cacheable(value = "statistics", key = "'firsteditions:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<FirstEditionsResponse> getFirstEditions(String country, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.firstEditions(country, params);
        return queryRunner.paged(sql, params, pageable, row -> FirstEditionsResponse.builder()
                .user(userIdentity(row, 0))
                .firstEditionCount(((Number) row[5]).longValue())
                .build());
    }

    @Cacheable(value = "statistics", key = "'firsteditionholders:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<FirstEditionHolderResponse> getFirstEditionHolders(String country, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.firstEditionHolders(country, params);
        return withItems(queryRunner.paged(sql, params, pageable, row -> FirstEditionHolderResponse.builder()
                .itemId((UUID) row[0])
                .linkId((UUID) row[5])
                .serialNumber(row[6] != null ? ((Number) row[6]).longValue() : null)
                .user(userIdentity(row, 7))
                .build()),
                FirstEditionHolderResponse::getItemId, (row, item) -> row.toBuilder().item(item).build());
    }

    @Cacheable(value = "statistics", key = "'completecollection:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<CollectionCompletionResponse> getMostCompleteCollection(String country, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.mostCompleteCollection(country, params);
        return queryRunner.paged(sql, params, pageable, row -> CollectionCompletionResponse.builder()
                .user(userIdentity(row, 0))
                .ownedCount(((Number) row[5]).longValue())
                .catalogTotal(((Number) row[6]).longValue())
                .completionPercent(row[7] != null ? ((Number) row[7]).doubleValue() : null)
                .build());
    }

    @Cacheable(value = "statistics", key = "'itemscarcity:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<ItemScarcityResponse> getItemScarcity(Pageable pageable) {
        return withItems(queryRunner.paged(SiteStatisticsQueries.ITEM_SCARCITY, Map.of(), pageable,
                row -> ItemScarcityResponse.builder()
                        .itemId((UUID) row[0])
                        .ownerCount(((Number) row[5]).longValue())
                        .instanceCount(((Number) row[6]).longValue())
                        .build()),
                ItemScarcityResponse::getItemId, (row, item) -> row.toBuilder().item(item).build());
    }

    public Page<ItemHolderResponse> getItemHolders(UUID itemId, List<String> modifiers, String search,
            ItemHolderSort sort, Long viewerId, Pageable pageable) {
        List<String> modifierKeys = normalizeModifiers(modifiers);
        if (sort == ItemHolderSort.FOLLOWING && viewerId == null) {
            throw new UnauthorizedException("Log in to sort holders by players you follow");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.itemHolders(itemId, modifierKeys, search, sort, viewerId, params);
        return queryRunner.paged(sql, params, pageable, row -> ItemHolderResponse.builder()
                .user(userIdentity(row, 0))
                .quantity(((Number) row[5]).longValue())
                .lowestSerial(row[6] != null ? ((Number) row[6]).longValue() : null)
                .acquiredAt(row[7] != null ? (Instant) row[7] : null)
                .modifiers(splitKeys((String) row[8]))
                .ranking(row[9] != null ? ((Number) row[9]).intValue() : null)
                .following(row[10] != null && (Boolean) row[10])
                .build());
    }

    @Cacheable(value = "statistics", key = "'biggesttraders:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<BiggestTraderResponse> getBiggestTraders(String country, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.biggestTraders(country, params);
        return queryRunner.paged(sql, params, pageable, row -> BiggestTraderResponse.builder()
                .user(userIdentity(row, 0))
                .tradeCount(((Number) row[5]).longValue())
                .itemsTraded(((Number) row[6]).longValue())
                .build());
    }

    @Cacheable(value = "statistics", key = "'essenceearned:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<EssenceEarnedResponse> getMostEssenceEarned(String country, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = SiteStatisticsQueries.mostEssenceEarned(country, params);
        return queryRunner.paged(sql, params, pageable, row -> EssenceEarnedResponse.builder()
                .user(userIdentity(row, 0))
                .essenceEarned(((Number) row[5]).longValue())
                .build());
    }

    private <T> Page<T> withItems(Page<T> page, Function<T, UUID> itemId,
            BiFunction<T, ItemResponse, T> attach) {
        Map<UUID, ItemResponse> items = itemRepository
                .findAllById(page.getContent().stream().map(itemId).distinct().toList()).stream()
                .collect(Collectors.toMap(Item::getId, ItemMapper::toItemResponse));
        return page.map(row -> attach.apply(row, items.get(itemId.apply(row))));
    }

    private static List<String> normalizeModifiers(List<String> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return List.of();
        }
        return modifiers.stream()
                .filter(m -> m != null && !m.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static List<String> splitKeys(String aggregated) {
        if (aggregated == null || aggregated.isBlank())
            return List.of();
        return List.of(aggregated.split(","));
    }
}
