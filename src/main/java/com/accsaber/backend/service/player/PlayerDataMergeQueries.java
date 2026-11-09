package com.accsaber.backend.service.player;

import java.util.List;

final class PlayerDataMergeQueries {

    static final String HAS_LOCKED_HOLDINGS = """
            SELECT EXISTS (SELECT 1 FROM user_item_links WHERE user_id = :secondary AND escrowed)
                OR EXISTS (SELECT 1 FROM users WHERE id = :secondary AND reserved_essence > 0)
                OR EXISTS (SELECT 1 FROM market_listings WHERE status = 'active'
                           AND (seller_id = :secondary OR current_bidder_id = :secondary))
                OR EXISTS (SELECT 1 FROM user_item_trades WHERE status = 'pending'
                           AND (from_user_id = :secondary OR to_user_id = :secondary))
            """;

    private static final String MOVE_UNLESS_HELD = """
            UPDATE %1$s s SET user_id = :primary
            WHERE s.user_id = :secondary %3$s AND NOT EXISTS (
                SELECT 1 FROM %1$s p WHERE p.user_id = :primary %4$s AND p.%2$s = s.%2$s)
            """;

    static final List<String> MOVES = List.of(
            """
            DELETE FROM user_milestone_links p USING user_milestone_links s
            WHERE p.user_id = :primary AND s.user_id = :secondary AND p.milestone_id = s.milestone_id
              AND NOT p.completed AND s.completed
            """,
            moveRows("user_milestone_links", "milestone_id"),
            moveRows("user_milestone_set_bonuses", "milestone_set_id"),
            moveActiveRows("user_campaign_scores", "campaign_difficulty_id"),
            """
            UPDATE user_campaigns p SET active = false FROM user_campaigns s
            WHERE p.user_id = :primary AND s.user_id = :secondary AND p.campaign_id = s.campaign_id
              AND p.active AND s.active AND p.status <> 'completed' AND s.status = 'completed'
            """,
            moveActiveRows("user_campaigns", "campaign_id"),
            "UPDATE campaigns SET creator_id = :primary WHERE creator_id = :secondary",
            moveActiveRows("campaign_collaborators", "campaign_id"),
            MOVE_UNLESS_HELD.formatted("user_missions", "parent_mission_id", "AND s.status = 'completed'", ""),
            moveRows("mission_contributions", "user_mission_id"),
            moveRows("user_event_profiles", "event_id"),
            """
            UPDATE supporter_accounts s SET user_id = :primary
            WHERE s.user_id = :secondary AND NOT EXISTS (SELECT 1 FROM supporter_accounts p WHERE p.user_id = :primary)
            """,
            "UPDATE kofi_events SET claimed_user_id = :primary WHERE claimed_user_id = :secondary");

    private PlayerDataMergeQueries() {
    }

    private static String moveRows(String table, String key) {
        return MOVE_UNLESS_HELD.formatted(table, key, "", "");
    }

    private static String moveActiveRows(String table, String key) {
        return MOVE_UNLESS_HELD.formatted(table, key, "AND s.active", "AND p.active");
    }
}
