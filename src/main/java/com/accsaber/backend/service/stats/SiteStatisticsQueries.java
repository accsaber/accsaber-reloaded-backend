package com.accsaber.backend.service.stats;

import static com.accsaber.backend.service.stats.StatsQueryRunner.countryClause;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.dto.request.item.ItemHolderSort;
import com.accsaber.backend.model.entity.item.ItemRarity;

final class SiteStatisticsQueries {

    static final String PLAYERS_PER_COUNTRY = """
            SELECT country, COUNT(*) AS cnt
            FROM users
            WHERE active = true AND banned = false AND country IS NOT NULL AND country != ''
            GROUP BY country
            ORDER BY cnt DESC
            """;

    static final String ITEM_SCARCITY = """
            SELECT i.id, i.name, i.icon_url, i.rarity, t.key, COUNT(DISTINCT u.id) AS owner_count,
                    COALESCE(SUM(CASE WHEN u.id IS NOT NULL THEN l.quantity END), 0) AS instance_count
            FROM items i
            JOIN item_types t ON t.id = i.type_id
            LEFT JOIN user_item_links l ON l.item_id = i.id
            LEFT JOIN users u ON u.id = l.user_id AND u.active = true AND u.banned = false
            WHERE i.tradeable = true AND i.active = true AND i.visible = true AND i.deprecated = false
            GROUP BY i.id, i.name, i.icon_url, i.rarity, t.key
            """
            + " ORDER BY instance_count ASC, owner_count ASC, " + rarityRank("i.rarity") + " DESC, i.name ASC";

    private SiteStatisticsQueries() {
    }

    static String highestAvgAp(UUID categoryId, String country, int minScores, Map<String, Object> params) {
        String sql = """
                SELECT d.id, d.map_id, m.song_name, m.song_subname, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url,
                        d.difficulty, c.id AS cat_id, c.name AS cat_name, m.beatsaver_code, d.characteristic,
                        AVG(s.weighted_ap) AS avg_weighted_ap, COUNT(*) AS score_count,
                        MAX(s.time_set) AS latest_time_set,
                        (SELECT s2.id FROM scores s2 WHERE s2.map_difficulty_id = d.id
                        AND s2.active = true ORDER BY s2.time_set DESC NULLS LAST LIMIT 1) AS latest_score_id
                FROM scores s
                JOIN map_difficulties d ON d.id = s.map_difficulty_id
                JOIN maps m ON m.id = d.map_id
                JOIN categories c ON c.id = d.category_id
                JOIN users u ON u.id = s.user_id
                WHERE s.active = true AND u.active = true AND u.banned = false
                """;
        sql += categoryFilter(categoryId, params);
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY d.id, d.map_id, m.song_name, m.song_subname, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url," +
                " d.difficulty, c.id, c.name, m.beatsaver_code, d.characteristic HAVING COUNT(*) >= :minScores" +
                " ORDER BY avg_weighted_ap DESC, score_count DESC, m.song_name ASC";
        params.put("minScores", (long) minScores);
        return sql;
    }

    static String mostRetriedMaps(UUID categoryId, String country, Map<String, Object> params) {
        String sql = """
                SELECT d.id, d.map_id, m.song_name, m.song_subname, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url,
                        d.difficulty, c.id AS cat_id, c.name AS cat_name, m.beatsaver_code, d.characteristic,
                        COUNT(*) AS superseded_count,
                        MAX(s.time_set) AS latest_time_set,
                        (SELECT s2.id FROM scores s2 WHERE s2.map_difficulty_id = d.id
                        AND s2.active = true ORDER BY s2.time_set DESC NULLS LAST LIMIT 1) AS latest_score_id
                FROM scores s
                JOIN map_difficulties d ON d.id = s.map_difficulty_id
                JOIN maps m ON m.id = d.map_id
                JOIN categories c ON c.id = d.category_id
                JOIN users u ON u.id = s.user_id
                WHERE s.active = false AND s.supersedes_reason = 'Score improved' AND u.active = true AND u.banned = false
                """;
        sql += categoryFilter(categoryId, params);
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY d.id, d.map_id, m.song_name, m.song_subname, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url," +
                " d.difficulty, c.id, c.name, m.beatsaver_code, d.characteristic ORDER BY superseded_count DESC, m.song_name ASC";
        return sql;
    }

    static String mostImprovements(UUID categoryId, String country, Map<String, Object> params) {
        String sql = """
                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, COUNT(*) AS improvement_count,
                        MAX(s.time_set) AS latest_time_set,
                        (SELECT s2.id FROM scores s2 WHERE s2.user_id = u.id AND s2.active = true
                                ORDER BY s2.time_set DESC NULLS LAST LIMIT 1) AS latest_score_id
                FROM scores s
                JOIN users u ON u.id = s.user_id
                """;
        if (categoryId != null) {
            sql += " JOIN map_difficulties d ON d.id = s.map_difficulty_id";
        }
        sql += " WHERE s.active = false AND s.supersedes_reason = 'Score improved' AND u.active = true AND u.banned = false";
        sql += categoryFilter(categoryId, params);
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country ORDER BY improvement_count DESC, u.name ASC";
        return sql;
    }

    static String mostMapImprovements(UUID categoryId, String country, Map<String, Object> params) {
        String sql = """
                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,
                        d.id AS diff_id, d.map_id, m.song_name, m.song_subname, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url,
                        d.difficulty, c.id AS cat_id, c.name AS cat_name, m.beatsaver_code, d.characteristic,
                        COUNT(*) AS improvement_count,
                        MAX(s.time_set) AS latest_time_set,
                        (SELECT s2.id FROM scores s2 WHERE s2.user_id = u.id AND s2.map_difficulty_id = d.id
                                AND s2.active = true LIMIT 1) AS latest_score_id
                FROM scores s
                JOIN users u ON u.id = s.user_id
                JOIN map_difficulties d ON d.id = s.map_difficulty_id
                JOIN maps m ON m.id = d.map_id
                JOIN categories c ON c.id = d.category_id
                WHERE s.active = false AND s.supersedes_reason = 'Score improved'
                        AND u.active = true AND u.banned = false
                """;
        sql += categoryFilter(categoryId, params);
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country," +
                " d.id, d.map_id, m.song_name, m.song_subname, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url," +
                " d.difficulty, c.id, c.name, m.beatsaver_code, d.characteristic ORDER BY improvement_count DESC, u.name ASC, m.song_name ASC";
        return sql;
    }

    static String milestoneCollectors(String country, Map<String, Object> params) {
        String sql = """
                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, COUNT(*) AS milestone_count
                FROM user_milestone_links uml
                JOIN users u ON u.id = uml.user_id
                WHERE uml.completed = true AND u.active = true AND u.banned = false
                """;
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                " ORDER BY milestone_count DESC, u.name ASC";
        return sql;
    }

    static String newPlayersPerDay(String trunc, String country, Map<String, Object> params) {
        return "SELECT day, cnt FROM (" +
                " SELECT date_trunc('" + trunc + "', created_at)::date AS day, COUNT(*) AS cnt" +
                " FROM users WHERE active = true AND banned = false AND created_at >= :since" +
                countryClause("country", country, params) +
                " GROUP BY day) sub WHERE cnt <= 4000 ORDER BY day";
    }

    static String scoresPerDay(String trunc, String country, Map<String, Object> params) {
        return "SELECT date_trunc('" + trunc + "', s.time_set)::date AS day, COUNT(*) AS cnt" +
                " FROM scores s JOIN users u ON u.id = s.user_id" +
                " WHERE u.active = true AND u.banned = false AND s.time_set >= :since" +
                countryClause("u.country", country, params) +
                " GROUP BY day ORDER BY day";
    }

    static String cumulativeAccounts(String trunc, String country, Map<String, Object> params) {
        return "SELECT day, SUM(cnt) OVER (ORDER BY day) AS cumulative FROM (" +
                " SELECT date_trunc('" + trunc + "', created_at)::date AS day, COUNT(*) AS cnt" +
                " FROM users WHERE active = true AND banned = false" +
                countryClause("country", country, params) + " GROUP BY day" +
                ") daily WHERE day >= :since ORDER BY day";
    }

    static String cumulativeScores(String trunc, String country, Map<String, Object> params) {
        return "SELECT day, SUM(cnt) OVER (ORDER BY day) AS cumulative FROM (" +
                " SELECT date_trunc('" + trunc + "', time_set)::date AS day, COUNT(*) AS cnt" +
                " FROM scores s JOIN users u ON u.id = s.user_id" +
                " WHERE u.active = true AND u.banned = false" +
                countryClause("u.country", country, params) + " GROUP BY day" +
                ") daily WHERE day >= :since ORDER BY day";
    }

    static String scoresPerCategory(String country, Map<String, Object> params) {
        return "SELECT c.name, COUNT(*) AS cnt" +
                " FROM scores s" +
                " JOIN map_difficulties d ON d.id = s.map_difficulty_id" +
                " JOIN categories c ON c.id = d.category_id" +
                " JOIN users u ON u.id = s.user_id" +
                " WHERE s.active = true AND u.active = true AND u.banned = false" +
                countryClause("u.country", country, params) +
                " GROUP BY c.name ORDER BY cnt DESC";
    }

    static String playersByHmd(String country, Map<String, Object> params) {
        return "SELECT hmd, COUNT(*) AS cnt FROM (" +
                " SELECT DISTINCT ON (s.user_id) s.hmd" +
                " FROM scores s" +
                " JOIN users u ON u.id = s.user_id" +
                " WHERE s.active = true AND u.active = true AND u.banned = false" +
                " AND s.hmd IS NOT NULL AND s.hmd != '' AND s.hmd != '0'" +
                countryClause("u.country", country, params) +
                " ORDER BY s.user_id, s.time_set DESC NULLS LAST" +
                ") latest GROUP BY hmd ORDER BY cnt DESC";
    }

    static String mostItems(String type, String modifier, String country, Map<String, Object> params) {
        String sql = """
                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, SUM(l.quantity) AS item_count
                FROM user_item_links l
                JOIN items i ON i.id = l.item_id
                JOIN item_types t ON t.id = i.type_id
                JOIN users u ON u.id = l.user_id
                WHERE i.tradeable = true AND u.active = true AND u.banned = false
                """;
        if (type != null)
            sql += " AND t.key = :type";
        if (modifier != null)
            sql += " AND EXISTS (SELECT 1 FROM user_item_link_modifiers lm" +
                    " JOIN item_modifiers im ON im.id = lm.modifier_id" +
                    " WHERE lm.user_item_link_id = l.id AND im.key = :modifier)";
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                " ORDER BY item_count DESC, u.name ASC";
        if (type != null)
            params.put("type", type);
        if (modifier != null)
            params.put("modifier", modifier);
        return sql;
    }

    static String mostCratesOpened(UUID crateId, String country, Map<String, Object> params) {
        String sql = """
                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, COUNT(*) AS crate_count
                FROM user_crate_opens o
                JOIN users u ON u.id = o.user_id
                WHERE u.active = true AND u.banned = false
                """;
        if (crateId != null)
            sql += " AND o.crate_item_id = :crateId";
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                " ORDER BY crate_count DESC, u.name ASC";
        if (crateId != null)
            params.put("crateId", crateId);
        return sql;
    }

    static String rarestUnboxed(String country, Map<String, Object> params) {
        String sql = """
                SELECT l.id, u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,
                        i.id, i.name, i.icon_url, i.rarity, t.key, l.serial_number,
                        COUNT(lm.modifier_id) AS modifier_count,
                        STRING_AGG(DISTINCT im.key, ',') AS modifier_keys,
                        ue.name AS unusual_effect
                FROM user_item_links l
                JOIN items i ON i.id = l.item_id
                JOIN item_types t ON t.id = i.type_id
                JOIN users u ON u.id = l.user_id
                LEFT JOIN user_item_link_modifiers lm ON lm.user_item_link_id = l.id
                LEFT JOIN item_modifiers im ON im.id = lm.modifier_id
                LEFT JOIN unusual_effects ue ON ue.id = l.unusual_effect_id
                WHERE l.source = 'crate_drop' AND i.tradeable = true
                        AND u.active = true AND u.banned = false
                """;
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY l.id, u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country," +
                " i.id, i.name, i.icon_url, i.rarity, t.key, l.serial_number, ue.name" +
                " ORDER BY modifier_count DESC, " + rarityRank("i.rarity") + " DESC," +
                " l.serial_number ASC NULLS LAST";
        return sql;
    }

    static String mostValuableInventory(String country, Map<String, Object> params) {
        String sql = """
                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,
                        COALESCE(SUM(i.worth * l.quantity), 0) AS items_value,
                        u.item_essence AS essence,
                        COALESCE(SUM(i.worth * l.quantity), 0) + u.item_essence AS total_value
                FROM users u
                LEFT JOIN user_item_links l ON l.user_id = u.id
                LEFT JOIN items i ON i.id = l.item_id AND i.tradeable = true
                WHERE u.active = true AND u.banned = false
                """;
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, u.item_essence" +
                " HAVING COALESCE(SUM(i.worth * l.quantity), 0) + u.item_essence > 0" +
                " ORDER BY total_value DESC, u.name ASC";
        return sql;
    }

    static String firstEditions(String country, Map<String, Object> params) {
        String sql = """
                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, COUNT(*) AS first_edition_count
                FROM user_item_links l
                JOIN items i ON i.id = l.item_id
                JOIN users u ON u.id = l.user_id
                WHERE l.serial_number = 1 AND i.tradeable = true
                        AND u.active = true AND u.banned = false
                """;
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                " ORDER BY first_edition_count DESC, u.name ASC";
        return sql;
    }

    static String firstEditionHolders(String country, Map<String, Object> params) {
        String sql = """
                SELECT i.id, i.name, i.icon_url, i.rarity, t.key, l.id, l.serial_number,
                        u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country
                FROM user_item_links l
                JOIN items i ON i.id = l.item_id
                JOIN item_types t ON t.id = i.type_id
                JOIN users u ON u.id = l.user_id
                WHERE l.serial_number = 1 AND i.tradeable = true
                        AND u.active = true AND u.banned = false
                """;
        sql += countryClause("u.country", country, params);
        sql += " ORDER BY " + rarityRank("i.rarity") + " DESC, i.name ASC";
        return sql;
    }

    static String mostCompleteCollection(String country, Map<String, Object> params) {
        String catalog = "(SELECT COUNT(*) FROM items ci WHERE ci.tradeable = true" +
                " AND ci.active = true AND ci.visible = true AND ci.deprecated = false)";
        String sql = "SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country," +
                " COUNT(DISTINCT i.id) AS owned_count," +
                " " + catalog + " AS catalog_total," +
                " ROUND(100.0 * COUNT(DISTINCT i.id) / NULLIF(" + catalog + ", 0), 2) AS completion_percent" +
                " FROM user_item_links l" +
                " JOIN items i ON i.id = l.item_id AND i.tradeable = true" +
                " AND i.active = true AND i.visible = true AND i.deprecated = false" +
                " JOIN users u ON u.id = l.user_id" +
                " WHERE u.active = true AND u.banned = false";
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                " ORDER BY owned_count DESC, u.name ASC";
        return sql;
    }

    static String itemHolders(UUID itemId, List<String> modifierKeys, String search, ItemHolderSort sort,
            Long viewerId, Map<String, Object> params) {
        boolean hasViewer = viewerId != null;
        String followingSelect = hasViewer ? "BOOL_OR(ur.id IS NOT NULL)" : "false";
        String followingJoin = hasViewer
                ? " LEFT JOIN user_relations ur ON ur.target_user_id = u.id AND ur.user_id = :viewerId"
                        + " AND ur.type = 'follower' AND ur.active = true"
                : "";

        String sql = "SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,"
                + " SUM(l.quantity) AS quantity, MIN(l.serial_number) AS lowest_serial,"
                + " MAX(l.awarded_at) AS acquired_at,"
                + " (SELECT STRING_AGG(DISTINCT im.key, ',') FROM user_item_links l2"
                + " JOIN user_item_link_modifiers lm ON lm.user_item_link_id = l2.id"
                + " JOIN item_modifiers im ON im.id = lm.modifier_id"
                + " WHERE l2.item_id = :itemId AND l2.user_id = u.id) AS modifier_keys,"
                + " MIN(ucs.ranking) AS ranking, " + followingSelect + " AS following"
                + " FROM user_item_links l"
                + " JOIN users u ON u.id = l.user_id AND u.active = true AND u.banned = false"
                + " LEFT JOIN user_category_statistics ucs ON ucs.user_id = u.id AND ucs.active = true"
                + " AND ucs.category_id = (SELECT id FROM categories WHERE code = 'overall' AND active = true LIMIT 1)"
                + followingJoin
                + " WHERE l.item_id = :itemId";
        if (!modifierKeys.isEmpty()) {
            sql += " AND (SELECT COUNT(DISTINCT im.key) FROM user_item_link_modifiers lm"
                    + " JOIN item_modifiers im ON im.id = lm.modifier_id"
                    + " WHERE lm.user_item_link_id = l.id AND im.key IN (:modifiers)) = :modifierCount";
        }
        if (search != null && !search.isBlank()) {
            sql += " AND u.id IN (SELECT sn.user_id FROM user_search_names sn"
                    + " WHERE sn.search_name LIKE CONCAT('%', search_normalize(:search), '%'))";
        }
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country ORDER BY " + holderOrderBy(sort);

        params.put("itemId", itemId);
        if (!modifierKeys.isEmpty()) {
            params.put("modifiers", modifierKeys);
            params.put("modifierCount", (long) modifierKeys.size());
        }
        if (search != null && !search.isBlank()) {
            params.put("search", search.trim());
        }
        if (hasViewer) {
            params.put("viewerId", viewerId);
        }
        return sql;
    }

    static String biggestTraders(String country, Map<String, Object> params) {
        String sql = """
                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,
                        COUNT(DISTINCT parties.trade_id) AS trade_count,
                        COALESCE(SUM(ti.quantity), 0) AS items_traded
                FROM (
                        SELECT from_user_id AS user_id, id AS trade_id FROM user_item_trades WHERE status = 'accepted'
                        UNION ALL
                        SELECT to_user_id AS user_id, id AS trade_id FROM user_item_trades WHERE status = 'accepted'
                ) parties
                JOIN users u ON u.id = parties.user_id
                JOIN user_item_trade_items ti ON ti.trade_id = parties.trade_id
                WHERE u.active = true AND u.banned = false
                """;
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                " ORDER BY trade_count DESC, items_traded DESC, u.name ASC";
        return sql;
    }

    static String mostEssenceEarned(String country, Map<String, Object> params) {
        String sql = """
                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,
                        COALESCE(SUM(d.essence_gained), 0) AS essence_earned
                FROM user_item_disintegrations d
                JOIN items i ON i.id = d.item_id
                JOIN users u ON u.id = d.user_id
                WHERE i.tradeable = true AND u.active = true AND u.banned = false
                """;
        sql += countryClause("u.country", country, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                " HAVING COALESCE(SUM(d.essence_gained), 0) > 0" +
                " ORDER BY essence_earned DESC, u.name ASC";
        return sql;
    }

    private static String categoryFilter(UUID categoryId, Map<String, Object> params) {
        if (categoryId == null)
            return "";
        params.put("categoryId", categoryId);
        return " AND d.category_id = :categoryId";
    }

    private static String holderOrderBy(ItemHolderSort sort) {
        return switch (sort == null ? ItemHolderSort.RECENT : sort) {
            case RANK -> "ranking ASC NULLS LAST, acquired_at DESC NULLS LAST, u.name ASC";
            case FOLLOWING -> "following DESC, ranking ASC NULLS LAST, u.name ASC";
            case RECENT -> "acquired_at DESC NULLS LAST, u.name ASC";
        };
    }

    private static String rarityRank(String column) {
        return "CASE " + column + ItemRarity.RANK_SQL_CASES;
    }
}
