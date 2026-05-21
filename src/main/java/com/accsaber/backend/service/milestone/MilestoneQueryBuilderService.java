package com.accsaber.backend.service.milestone;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.FilterSpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.HavingSpec;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSchemaResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSchemaResponse.ColumnInfo;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.score.Score;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MilestoneQueryBuilderService {

    private final EntityManager entityManager;

    private enum ValueType {
        BIGDECIMAL, INTEGER, LONG, BOOLEAN, STRING, INSTANT, DOUBLE, UUID, ENTITY, ENUM
    }

    private record ColumnDef(String jpqlExpr, ValueType type, Class<? extends Enum<?>> enumClass) {
        static ColumnDef bd(String e) {
            return new ColumnDef(e, ValueType.BIGDECIMAL, null);
        }

        static ColumnDef int_(String e) {
            return new ColumnDef(e, ValueType.INTEGER, null);
        }

        static ColumnDef long_(String e) {
            return new ColumnDef(e, ValueType.LONG, null);
        }

        static ColumnDef bool_(String e) {
            return new ColumnDef(e, ValueType.BOOLEAN, null);
        }

        static ColumnDef str(String e) {
            return new ColumnDef(e, ValueType.STRING, null);
        }

        static ColumnDef instant(String e) {
            return new ColumnDef(e, ValueType.INSTANT, null);
        }

        static ColumnDef dbl(String e) {
            return new ColumnDef(e, ValueType.DOUBLE, null);
        }

        static ColumnDef uuid(String e) {
            return new ColumnDef(e, ValueType.UUID, null);
        }

        static ColumnDef entity(String e) {
            return new ColumnDef(e, ValueType.ENTITY, null);
        }

        static ColumnDef enum_(String e, Class<? extends Enum<?>> c) {
            return new ColumnDef(e, ValueType.ENUM, c);
        }
    }

    private static final Map<Class<? extends Enum<?>>, Function<String, Object>> ENUM_PARSERS = Map.of(
            MapDifficultyStatus.class, s -> {
                try {
                    return MapDifficultyStatus.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    return MapDifficultyStatus.fromDbValue(s);
                }
            },
            Difficulty.class, s -> {
                try {
                    return Difficulty.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    return Difficulty.fromDbValue(s);
                }
            });

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object coerce(Object value, ColumnDef def) {
        if (value == null) {
            throw new ValidationException("Filter value cannot be null");
        }
        try {
            return switch (def.type()) {
                case BIGDECIMAL -> value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
                case INTEGER -> value instanceof Integer i ? i : Integer.parseInt(value.toString());
                case LONG -> value instanceof Long l ? l : Long.parseLong(value.toString());
                case BOOLEAN -> value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
                case STRING -> value.toString();
                case INSTANT -> value instanceof Instant i ? i : Instant.parse(value.toString());
                case DOUBLE -> value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
                case UUID -> value instanceof UUID u ? u : UUID.fromString(value.toString());
                case ENUM -> ENUM_PARSERS.getOrDefault(def.enumClass(),
                        s -> Enum.valueOf((Class<Enum>) (Class<?>) def.enumClass(), s.toUpperCase()))
                        .apply(value.toString());
                case ENTITY -> value;
            };
        } catch (java.time.format.DateTimeParseException | IllegalArgumentException e) {
            throw new ValidationException(
                    "Invalid value '" + value + "' for type " + def.type() + ": " + e.getMessage());
        }
    }

    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of("COUNT", "COUNT_DISTINCT", "MAX", "MIN", "SUM",
            "AVG", "PLAIN");

    private static final Set<String> OPERATORS = Set.of(">", ">=", "<", "<=", "=", "!=");

    private static final Set<String> SUBQUERY_ONLY_OPERATORS = Set.of("IN", "NOT IN", "EXISTS", "NOT EXISTS");

    private static final Set<String> TRANSFORM_FUNCTIONS = Set.of("MOD", "INTERVAL_SUBTRACT");

    private record TableConfig(String entity, String alias, String userIdPath,
            String categoryIdPath, String rankedStatusPath) {
    }

    private static final Map<String, TableConfig> TABLE_CONFIG = new LinkedHashMap<>();

    static {
        TABLE_CONFIG.put("scores", new TableConfig(
                "Score", "s", "s.user.id", "s.mapDifficulty.category.id",
                "s.mapDifficulty.status"));
        TABLE_CONFIG.put("user_category_statistics", new TableConfig(
                "UserCategoryStatistics", "ucs", "ucs.user.id", "ucs.category.id", null));
        TABLE_CONFIG.put("users", new TableConfig(
                "User", "u", "u.id", null, null));
        TABLE_CONFIG.put("user_milestone_links", new TableConfig(
                "UserMilestoneLink", "uml", "uml.user.id", null, null));
        TABLE_CONFIG.put("maps", new TableConfig(
                "Map", "mp", null, null, null));
        TABLE_CONFIG.put("map_difficulties", new TableConfig(
                "MapDifficulty", "md", null, "md.category.id", "md.status"));
        TABLE_CONFIG.put("map_difficulty_statistics", new TableConfig(
                "MapDifficultyStatistics", "mds", null, "mds.mapDifficulty.category.id",
                "mds.mapDifficulty.status"));
        TABLE_CONFIG.put("map_difficulty_complexities", new TableConfig(
                "MapDifficultyComplexity", "mdc", null, "mdc.mapDifficulty.category.id",
                "mdc.mapDifficulty.status"));
        TABLE_CONFIG.put("categories", new TableConfig(
                "Category", "cat", null, null, null));
        TABLE_CONFIG.put("modifiers", new TableConfig(
                "Modifier", "mod", null, null, null));
        TABLE_CONFIG.put("milestones", new TableConfig(
                "Milestone", "mil", null, null, null));
        TABLE_CONFIG.put("milestone_sets", new TableConfig(
                "MilestoneSet", "mset", null, null, null));
        TABLE_CONFIG.put("level_thresholds", new TableConfig(
                "LevelThreshold", "lt", null, null, null));
        TABLE_CONFIG.put("score_modifier_links", new TableConfig(
                "ScoreModifierLink", "sml", null, null, null));
    }

    private static final Map<String, Map<String, ColumnDef>> COLUMN_ALLOWLIST = new LinkedHashMap<>();

    static {
        // --- scores ---
        Map<String, ColumnDef> scores = new LinkedHashMap<>();
        // direct
        scores.put("id", ColumnDef.uuid("s.id"));
        scores.put("ap", ColumnDef.bd("s.ap"));
        scores.put("weighted_ap", ColumnDef.bd("s.weightedAp"));
        scores.put("score", ColumnDef.int_("s.score"));
        scores.put("score_no_mods", ColumnDef.int_("s.scoreNoMods"));
        scores.put("rank", ColumnDef.int_("s.rank"));
        scores.put("rank_when_set", ColumnDef.int_("s.rankWhenSet"));
        scores.put("max_combo", ColumnDef.int_("s.maxCombo"));
        scores.put("misses", ColumnDef.int_("s.misses"));
        scores.put("bad_cuts", ColumnDef.int_("s.badCuts"));
        scores.put("wall_hits", ColumnDef.int_("s.wallHits"));
        scores.put("bomb_hits", ColumnDef.int_("s.bombHits"));
        scores.put("pauses", ColumnDef.int_("s.pauses"));
        scores.put("streak_115", ColumnDef.int_("s.streak115"));
        scores.put("play_count", ColumnDef.int_("s.playCount"));
        scores.put("active", ColumnDef.bool_("s.active"));
        scores.put("partial", ColumnDef.bool_("s.partial"));
        scores.put("xp_gained", ColumnDef.bd("s.xpGained"));
        scores.put("reweight_derivative", ColumnDef.bool_("s.reweightDerivative"));
        scores.put("time_set", ColumnDef.instant("s.timeSet"));
        scores.put("supersedes_id", ColumnDef.uuid("s.supersedes.id"));
        scores.put("supersedes_reason", ColumnDef.str("s.supersedesReason"));
        scores.put("supersedes_time_set", ColumnDef.instant("s.supersedes.timeSet"));
        scores.put("map_difficulty_id", ColumnDef.entity("s.mapDifficulty"));
        scores.put("map_difficulty_uuid_id", ColumnDef.uuid("s.mapDifficulty.id"));
        scores.put("accuracy", ColumnDef.dbl("CAST(s.score AS Double) / s.mapDifficulty.maxScore"));
        // via user
        scores.put("user_id", ColumnDef.long_("s.user.id"));
        scores.put("user_country", ColumnDef.str("s.user.country"));
        scores.put("user_active", ColumnDef.bool_("s.user.active"));
        scores.put("user_banned", ColumnDef.bool_("s.user.banned"));
        scores.put("user_total_xp", ColumnDef.bd("s.user.totalXp"));
        // via mapDifficulty
        scores.put("map_id", ColumnDef.uuid("s.mapDifficulty.map.id"));
        scores.put("map_difficulty_status", ColumnDef.enum_("s.mapDifficulty.status", MapDifficultyStatus.class));
        scores.put("map_difficulty_characteristic", ColumnDef.str("s.mapDifficulty.characteristic"));
        scores.put("map_difficulty_difficulty", ColumnDef.enum_("s.mapDifficulty.difficulty", Difficulty.class));
        scores.put("map_difficulty_max_score", ColumnDef.int_("s.mapDifficulty.maxScore"));
        scores.put("map_difficulty_ranked_at", ColumnDef.instant("s.mapDifficulty.rankedAt"));
        scores.put("map_difficulty_category_id", ColumnDef.uuid("s.mapDifficulty.category.id"));
        // via mapDifficulty.map
        scores.put("song_name", ColumnDef.str("s.mapDifficulty.map.songName"));
        scores.put("song_author", ColumnDef.str("s.mapDifficulty.map.songAuthor"));
        scores.put("map_author", ColumnDef.str("s.mapDifficulty.map.mapAuthor"));
        scores.put("song_hash", ColumnDef.str("s.mapDifficulty.map.songHash"));
        // via mapDifficulty.category
        scores.put("category_name", ColumnDef.str("s.mapDifficulty.category.name"));
        scores.put("category_code", ColumnDef.str("s.mapDifficulty.category.code"));
        scores.put("category_count_for_overall", ColumnDef.bool_("s.mapDifficulty.category.countForOverall"));
        COLUMN_ALLOWLIST.put("scores", scores);

        // --- user_category_statistics ---
        Map<String, ColumnDef> ucs = new LinkedHashMap<>();
        ucs.put("id", ColumnDef.uuid("ucs.id"));
        ucs.put("ap", ColumnDef.bd("ucs.ap"));
        ucs.put("average_acc", ColumnDef.bd("ucs.averageAcc"));
        ucs.put("average_ap", ColumnDef.bd("ucs.averageAp"));
        ucs.put("ranked_plays", ColumnDef.int_("ucs.rankedPlays"));
        ucs.put("ranking", ColumnDef.int_("ucs.ranking"));
        ucs.put("country_ranking", ColumnDef.int_("ucs.countryRanking"));
        ucs.put("active", ColumnDef.bool_("ucs.active"));
        // via category
        ucs.put("category_id", ColumnDef.uuid("ucs.category.id"));
        ucs.put("category_name", ColumnDef.str("ucs.category.name"));
        ucs.put("category_code", ColumnDef.str("ucs.category.code"));
        ucs.put("category_count_for_overall", ColumnDef.bool_("ucs.category.countForOverall"));
        // via user
        ucs.put("user_id", ColumnDef.long_("ucs.user.id"));
        ucs.put("user_country", ColumnDef.str("ucs.user.country"));
        ucs.put("user_active", ColumnDef.bool_("ucs.user.active"));
        ucs.put("user_banned", ColumnDef.bool_("ucs.user.banned"));
        ucs.put("user_total_xp", ColumnDef.bd("ucs.user.totalXp"));
        COLUMN_ALLOWLIST.put("user_category_statistics", ucs);

        // --- users ---
        Map<String, ColumnDef> users = new LinkedHashMap<>();
        users.put("name", ColumnDef.str("u.name"));
        users.put("total_xp", ColumnDef.bd("u.totalXp"));
        users.put("active", ColumnDef.bool_("u.active"));
        users.put("banned", ColumnDef.bool_("u.banned"));
        users.put("country", ColumnDef.str("u.country"));
        users.put("hmd", ColumnDef.str("u.hmd"));
        COLUMN_ALLOWLIST.put("users", users);

        // --- user_milestone_links ---
        Map<String, ColumnDef> uml = new LinkedHashMap<>();
        uml.put("id", ColumnDef.uuid("uml.id"));
        uml.put("completed", ColumnDef.bool_("uml.completed"));
        uml.put("progress", ColumnDef.bd("uml.progress"));
        uml.put("completed_at", ColumnDef.instant("uml.completedAt"));
        // via user
        uml.put("user_id", ColumnDef.long_("uml.user.id"));
        uml.put("user_country", ColumnDef.str("uml.user.country"));
        uml.put("user_active", ColumnDef.bool_("uml.user.active"));
        uml.put("user_banned", ColumnDef.bool_("uml.user.banned"));
        // via milestone
        uml.put("milestone_id", ColumnDef.uuid("uml.milestone.id"));
        uml.put("milestone_xp", ColumnDef.bd("uml.milestone.xp"));
        uml.put("milestone_target_value", ColumnDef.bd("uml.milestone.targetValue"));
        uml.put("milestone_type", ColumnDef.str("uml.milestone.type"));
        uml.put("milestone_tier", ColumnDef.str("uml.milestone.tier"));
        uml.put("milestone_set_id", ColumnDef.uuid("uml.milestone.milestoneSet.id"));
        COLUMN_ALLOWLIST.put("user_milestone_links", uml);

        // --- maps ---
        Map<String, ColumnDef> maps = new LinkedHashMap<>();
        maps.put("id", ColumnDef.uuid("mp.id"));
        maps.put("song_name", ColumnDef.str("mp.songName"));
        maps.put("song_author", ColumnDef.str("mp.songAuthor"));
        maps.put("map_author", ColumnDef.str("mp.mapAuthor"));
        maps.put("song_hash", ColumnDef.str("mp.songHash"));
        maps.put("active", ColumnDef.bool_("mp.active"));
        COLUMN_ALLOWLIST.put("maps", maps);

        // --- map_difficulties ---
        Map<String, ColumnDef> md = new LinkedHashMap<>();
        md.put("id", ColumnDef.uuid("md.id"));
        md.put("map_entity", ColumnDef.entity("md.map"));
        md.put("status", ColumnDef.enum_("md.status", MapDifficultyStatus.class));
        md.put("max_score", ColumnDef.int_("md.maxScore"));
        md.put("active", ColumnDef.bool_("md.active"));
        md.put("difficulty", ColumnDef.enum_("md.difficulty", Difficulty.class));
        md.put("characteristic", ColumnDef.str("md.characteristic"));
        md.put("ranked_at", ColumnDef.instant("md.rankedAt"));
        // via map
        md.put("map_id", ColumnDef.uuid("md.map.id"));
        md.put("song_name", ColumnDef.str("md.map.songName"));
        md.put("song_author", ColumnDef.str("md.map.songAuthor"));
        md.put("map_author", ColumnDef.str("md.map.mapAuthor"));
        md.put("song_hash", ColumnDef.str("md.map.songHash"));
        // via category
        md.put("category_id", ColumnDef.uuid("md.category.id"));
        md.put("category_name", ColumnDef.str("md.category.name"));
        md.put("category_code", ColumnDef.str("md.category.code"));
        md.put("category_count_for_overall", ColumnDef.bool_("md.category.countForOverall"));
        COLUMN_ALLOWLIST.put("map_difficulties", md);

        // --- map_difficulty_statistics ---
        Map<String, ColumnDef> mds = new LinkedHashMap<>();
        mds.put("id", ColumnDef.uuid("mds.id"));
        mds.put("max_ap", ColumnDef.bd("mds.maxAp"));
        mds.put("min_ap", ColumnDef.bd("mds.minAp"));
        mds.put("average_ap", ColumnDef.bd("mds.averageAp"));
        mds.put("total_scores", ColumnDef.int_("mds.totalScores"));
        mds.put("active", ColumnDef.bool_("mds.active"));
        // via mapDifficulty
        mds.put("map_difficulty_id", ColumnDef.uuid("mds.mapDifficulty.id"));
        mds.put("map_difficulty_status", ColumnDef.enum_("mds.mapDifficulty.status", MapDifficultyStatus.class));
        mds.put("map_difficulty_difficulty", ColumnDef.enum_("mds.mapDifficulty.difficulty", Difficulty.class));
        mds.put("map_difficulty_characteristic", ColumnDef.str("mds.mapDifficulty.characteristic"));
        mds.put("map_difficulty_max_score", ColumnDef.int_("mds.mapDifficulty.maxScore"));
        // via map
        mds.put("map_id", ColumnDef.uuid("mds.mapDifficulty.map.id"));
        mds.put("song_name", ColumnDef.str("mds.mapDifficulty.map.songName"));
        mds.put("song_hash", ColumnDef.str("mds.mapDifficulty.map.songHash"));
        // via category
        mds.put("category_id", ColumnDef.uuid("mds.mapDifficulty.category.id"));
        mds.put("category_name", ColumnDef.str("mds.mapDifficulty.category.name"));
        mds.put("category_code", ColumnDef.str("mds.mapDifficulty.category.code"));
        COLUMN_ALLOWLIST.put("map_difficulty_statistics", mds);

        // --- map_difficulty_complexities ---
        Map<String, ColumnDef> mdc = new LinkedHashMap<>();
        mdc.put("id", ColumnDef.uuid("mdc.id"));
        mdc.put("map_difficulty_uuid_id", ColumnDef.uuid("mdc.mapDifficulty.id"));
        mdc.put("complexity", ColumnDef.bd("mdc.complexity"));
        mdc.put("active", ColumnDef.bool_("mdc.active"));
        // via mapDifficulty
        mdc.put("map_difficulty_status", ColumnDef.enum_("mdc.mapDifficulty.status", MapDifficultyStatus.class));
        mdc.put("map_difficulty_difficulty", ColumnDef.enum_("mdc.mapDifficulty.difficulty", Difficulty.class));
        mdc.put("map_difficulty_characteristic", ColumnDef.str("mdc.mapDifficulty.characteristic"));
        mdc.put("map_difficulty_max_score", ColumnDef.int_("mdc.mapDifficulty.maxScore"));
        // via map
        mdc.put("map_id", ColumnDef.uuid("mdc.mapDifficulty.map.id"));
        mdc.put("song_name", ColumnDef.str("mdc.mapDifficulty.map.songName"));
        mdc.put("song_hash", ColumnDef.str("mdc.mapDifficulty.map.songHash"));
        // via category
        mdc.put("category_id", ColumnDef.uuid("mdc.mapDifficulty.category.id"));
        mdc.put("category_name", ColumnDef.str("mdc.mapDifficulty.category.name"));
        mdc.put("category_code", ColumnDef.str("mdc.mapDifficulty.category.code"));
        COLUMN_ALLOWLIST.put("map_difficulty_complexities", mdc);

        // --- categories ---
        Map<String, ColumnDef> cat = new LinkedHashMap<>();
        cat.put("id", ColumnDef.uuid("cat.id"));
        cat.put("name", ColumnDef.str("cat.name"));
        cat.put("code", ColumnDef.str("cat.code"));
        cat.put("count_for_overall", ColumnDef.bool_("cat.countForOverall"));
        cat.put("active", ColumnDef.bool_("cat.active"));
        COLUMN_ALLOWLIST.put("categories", cat);

        // --- modifiers ---
        Map<String, ColumnDef> mod = new LinkedHashMap<>();
        mod.put("id", ColumnDef.uuid("mod.id"));
        mod.put("name", ColumnDef.str("mod.name"));
        mod.put("code", ColumnDef.str("mod.code"));
        mod.put("multiplier", ColumnDef.bd("mod.multiplier"));
        COLUMN_ALLOWLIST.put("modifiers", mod);

        // --- milestones ---
        Map<String, ColumnDef> mil = new LinkedHashMap<>();
        mil.put("id", ColumnDef.uuid("mil.id"));
        mil.put("title", ColumnDef.str("mil.title"));
        mil.put("type", ColumnDef.str("mil.type"));
        mil.put("tier", ColumnDef.str("mil.tier"));
        mil.put("xp", ColumnDef.bd("mil.xp"));
        mil.put("target_value", ColumnDef.bd("mil.targetValue"));
        mil.put("active", ColumnDef.bool_("mil.active"));
        mil.put("comparison", ColumnDef.str("mil.comparison"));
        mil.put("set_id", ColumnDef.uuid("mil.milestoneSet.id"));
        COLUMN_ALLOWLIST.put("milestones", mil);

        // --- milestone_sets ---
        Map<String, ColumnDef> mset = new LinkedHashMap<>();
        mset.put("id", ColumnDef.uuid("mset.id"));
        mset.put("title", ColumnDef.str("mset.title"));
        mset.put("set_bonus_xp", ColumnDef.bd("mset.setBonusXp"));
        mset.put("active", ColumnDef.bool_("mset.active"));
        COLUMN_ALLOWLIST.put("milestone_sets", mset);

        // --- level_thresholds ---
        Map<String, ColumnDef> lt = new LinkedHashMap<>();
        lt.put("level", ColumnDef.int_("lt.level"));
        lt.put("title", ColumnDef.str("lt.title"));
        COLUMN_ALLOWLIST.put("level_thresholds", lt);

        // --- score_modifier_links ---
        Map<String, ColumnDef> sml = new LinkedHashMap<>();
        sml.put("id", ColumnDef.uuid("sml.id"));
        sml.put("score_id", ColumnDef.uuid("sml.score.id"));
        sml.put("modifier_id", ColumnDef.uuid("sml.modifier.id"));
        COLUMN_ALLOWLIST.put("score_modifier_links", sml);
    }

    public void validate(MilestoneQuerySpec spec) {
        validateSpec(spec, false);
    }

    private void validateSpec(MilestoneQuerySpec spec, boolean isSubquery) {
        if (spec == null || spec.select() == null || spec.from() == null) {
            throw new ValidationException("query_spec must include select and from fields");
        }
        TableConfig table = TABLE_CONFIG.get(spec.from());
        if (table == null) {
            throw new ValidationException("Unsupported table: " + spec.from() +
                    ". Allowed: " + TABLE_CONFIG.keySet());
        }
        String fn = spec.select().function().toUpperCase();
        if (!AGGREGATE_FUNCTIONS.contains(fn)) {
            throw new ValidationException("Unsupported function: " + spec.select().function() +
                    ". Allowed: " + AGGREGATE_FUNCTIONS);
        }
        if (fn.equals("PLAIN") && !isSubquery) {
            throw new ValidationException("PLAIN function is only valid in subqueries");
        }
        Map<String, ColumnDef> columns = COLUMN_ALLOWLIST.get(spec.from());
        if (!columns.containsKey(spec.select().column())) {
            throw new ValidationException("Unsupported select column '" + spec.select().column() +
                    "' for table '" + spec.from() + "'");
        }
        if (spec.filters() != null) {
            for (FilterSpec filter : spec.filters()) {
                validateFilter(filter, columns, spec.from());
            }
        }
        if (spec.orGroups() != null) {
            for (List<FilterSpec> group : spec.orGroups()) {
                if (group == null || group.isEmpty()) {
                    throw new ValidationException("or_groups entries must not be null or empty");
                }
                for (FilterSpec filter : group) {
                    validateFilter(filter, columns, spec.from());
                }
            }
        }
        if (spec.having() != null) {
            HavingSpec having = spec.having();
            if (!AGGREGATE_FUNCTIONS.contains(having.function().toUpperCase())) {
                throw new ValidationException("Unsupported having function: " + having.function());
            }
            if (!columns.containsKey(having.column())) {
                throw new ValidationException("Unsupported having column '" + having.column() +
                        "' for table '" + spec.from() + "'");
            }
            if (!OPERATORS.contains(having.operator())) {
                throw new ValidationException("Unsupported having operator: " + having.operator());
            }
            if (having.value() == null && having.valueQuery() == null) {
                throw new ValidationException("Having must have either value or value_query");
            }
            if (having.valueQuery() != null) {
                validateSpec(having.valueQuery(), false);
            }
        }
        if (spec.divisor() != null) {
            validateSpec(spec.divisor(), false);
        }
        if (spec.groupBy() != null && !spec.groupBy().isEmpty()) {
            for (var gb : spec.groupBy()) {
                if (!columns.containsKey(gb.column())) {
                    throw new ValidationException("Unsupported group_by column '" + gb.column() +
                            "' for table '" + spec.from() + "'");
                }
                if (gb.cast() != null && !"DATE".equals(gb.cast().toUpperCase())) {
                    throw new ValidationException("Unsupported group_by cast: " + gb.cast() + ". Allowed: DATE");
                }
            }
            if (spec.outerFunction() == null) {
                throw new ValidationException("outer_function is required when group_by is present");
            }
            if (!AGGREGATE_FUNCTIONS.contains(spec.outerFunction().toUpperCase())) {
                throw new ValidationException("Unsupported outer_function: " + spec.outerFunction());
            }
        }
        if (spec.orderBy() != null) {
            for (var ob : spec.orderBy()) {
                if (!columns.containsKey(ob.column())) {
                    throw new ValidationException("Unsupported order_by column '" + ob.column() +
                            "' for table '" + spec.from() + "'");
                }
                if (ob.direction() != null && !"ASC".equalsIgnoreCase(ob.direction())
                        && !"DESC".equalsIgnoreCase(ob.direction())) {
                    throw new ValidationException("Unsupported order_by direction: " + ob.direction());
                }
            }
            if (spec.limit() == null) {
                throw new ValidationException("limit is required when order_by is present");
            }
        }
    }

    private void validateTransform(MilestoneQuerySpec.TransformSpec transform) {
        if (!TRANSFORM_FUNCTIONS.contains(transform.function())) {
            throw new ValidationException("Unsupported transform function: " + transform.function()
                    + ". Allowed: " + TRANSFORM_FUNCTIONS);
        }
        if (transform.argument() == null) {
            throw new ValidationException("Transform argument must not be null");
        }
    }

    private void validateFilter(FilterSpec filter, Map<String, ColumnDef> columns, String table) {
        if (!columns.containsKey(filter.column())) {
            throw new ValidationException("Unsupported filter column '" + filter.column() +
                    "' for table '" + table + "'");
        }
        boolean isSubqueryOp = SUBQUERY_ONLY_OPERATORS.contains(filter.operator());
        if (!OPERATORS.contains(filter.operator()) && !isSubqueryOp) {
            throw new ValidationException("Unsupported operator: " + filter.operator() +
                    ". Allowed: " + OPERATORS + " or subquery operators: " + SUBQUERY_ONLY_OPERATORS);
        }
        if (filter.subquery() != null) {
            validateSpec(filter.subquery(), true);
        } else if (isSubqueryOp) {
            throw new ValidationException(
                    "Operator '" + filter.operator() + "' requires a subquery on column: " + filter.column());
        } else if (filter.columnRef() != null) {
            String ref = filter.columnRef();
            String refCol = ref.startsWith("OUTER.") ? ref.substring(6) : ref;
            if (!columns.containsKey(refCol)) {
                throw new ValidationException("Unsupported column_ref '" + refCol +
                        "' for table '" + table + "'");
            }
        } else {
            if (filter.value() == null) {
                throw new ValidationException("Filter value must not be null for column: " + filter.column());
            }
        }
        if (filter.transform() != null) {
            validateTransform(filter.transform());
        }
        if (filter.columnRefTransform() != null) {
            validateTransform(filter.columnRefTransform());
        }
        if ("EXISTS".equals(filter.operator()) || "NOT EXISTS".equals(filter.operator())) {
            if (filter.subquery() == null) {
                throw new ValidationException(
                        "Operator '" + filter.operator() + "' requires a subquery");
            }
        }
    }

    public BigDecimal evaluate(MilestoneQuerySpec spec, Long userId, UUID categoryId) {
        BigDecimal mainResult = evaluateSingle(spec, userId, categoryId);

        if (spec.divisor() != null) {
            BigDecimal divisorResult = evaluateSingle(spec.divisor(), userId, categoryId);
            if (mainResult == null || divisorResult == null
                    || divisorResult.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return mainResult.divide(divisorResult, 6, java.math.RoundingMode.HALF_UP);
        }

        return mainResult;
    }

    public boolean requiresIndividualEvaluation(MilestoneQuerySpec spec) {
        if (spec.having() != null) return true;
        if (spec.divisor() != null) return true;
        return requiresNativeSql(spec);
    }

    public boolean requiresNativeSql(MilestoneQuerySpec spec) {
        if (spec.groupBy() != null && !spec.groupBy().isEmpty()) return true;
        if (spec.orderBy() != null && !spec.orderBy().isEmpty()) return true;
        if (filterRequiresNativeSql(spec.filters())) return true;
        if (spec.orGroups() != null) {
            for (List<FilterSpec> group : spec.orGroups()) {
                if (filterRequiresNativeSql(group)) return true;
            }
        }
        return false;
    }

    private boolean filterRequiresNativeSql(List<FilterSpec> filters) {
        if (filters == null) return false;
        for (FilterSpec f : filters) {
            if (f.columnRef() != null) return true;
            if (f.transform() != null && "INTERVAL_SUBTRACT".equals(f.transform().function())) return true;
            if (f.columnRefTransform() != null && "INTERVAL_SUBTRACT".equals(f.columnRefTransform().function()))
                return true;
            if (f.subquery() != null && requiresNativeSql(f.subquery())) return true;
        }
        return false;
    }

    private BigDecimal evaluateSingle(MilestoneQuerySpec spec, Long userId, UUID categoryId) {
        if (requiresNativeSql(spec)) {
            return evaluateNativeSql(spec, userId, categoryId);
        }

        TableConfig table = TABLE_CONFIG.get(spec.from());
        Map<String, ColumnDef> columns = COLUMN_ALLOWLIST.get(spec.from());

        ColumnDef selectCol = columns.get(spec.select().column());
        String fn = spec.select().function().toUpperCase();
        String selectExpr = buildSelectClause(fn, selectCol.jpqlExpr());

        AtomicInteger paramCounter = new AtomicInteger(0);
        List<Object[]> extraParams = new ArrayList<>();
        List<String> conditions = buildConditions(spec, table, columns, userId, categoryId,
                extraParams, paramCounter);

        String finalSelect;
        if (spec.having() != null) {
            HavingSpec having = spec.having();
            ColumnDef havingCol = columns.get(having.column());
            String havingFn = having.function().toUpperCase();
            String havingExpr = buildSelectClause(havingFn, havingCol.jpqlExpr());
            String havingParam = "having" + paramCounter.getAndIncrement();
            finalSelect = "CASE WHEN " + havingExpr + " " + having.operator() + " :" + havingParam
                    + " THEN " + selectExpr + " ELSE NULL END";
            Object havingValue;
            if (having.valueQuery() != null) {
                BigDecimal resolved = evaluateSingle(having.valueQuery(), userId, categoryId);
                havingValue = resolved != null ? resolved : BigDecimal.ZERO;
            } else {
                havingValue = coerce(having.value(), havingCol);
            }
            extraParams.add(new Object[] { havingParam, havingValue });
        } else {
            finalSelect = selectExpr;
        }

        StringBuilder jpql = new StringBuilder()
                .append("SELECT ").append(finalSelect)
                .append(" FROM ").append(table.entity()).append(" ").append(table.alias());
        if (!conditions.isEmpty()) {
            jpql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        Query query = entityManager.createQuery(jpql.toString());
        bindParameters(query, table, userId, categoryId, extraParams);
        return toBigDecimal(query.getSingleResult());
    }

    private List<String> buildConditions(MilestoneQuerySpec spec, TableConfig table,
            Map<String, ColumnDef> columns, Long userId, UUID categoryId,
            List<Object[]> extraParams, AtomicInteger paramCounter) {
        List<String> conditions = new ArrayList<>();
        if (table.userIdPath() != null) {
            conditions.add(table.userIdPath() + " = :userId");
        }
        if (categoryId != null && table.categoryIdPath() != null) {
            conditions.add(table.categoryIdPath() + " = :categoryId");
        }
        if (table.rankedStatusPath() != null) {
            conditions.add(table.rankedStatusPath() + " = :rankedStatus");
        }

        List<FilterSpec> filters = spec.filters() != null ? spec.filters() : List.of();
        for (FilterSpec filter : filters) {
            conditions.add(buildSingleFilterCondition(filter, columns, table.alias(),
                    extraParams, paramCounter));
        }

        if (spec.orGroups() != null && !spec.orGroups().isEmpty()) {
            List<String> orBranches = new ArrayList<>();
            for (List<FilterSpec> group : spec.orGroups()) {
                List<String> groupConditions = new ArrayList<>();
                for (FilterSpec filter : group) {
                    groupConditions.add(buildSingleFilterCondition(filter, columns, table.alias(),
                            extraParams, paramCounter));
                }
                orBranches.add("(" + String.join(" AND ", groupConditions) + ")");
            }
            conditions.add("(" + String.join(" OR ", orBranches) + ")");
        }

        return conditions;
    }

    private String buildSingleFilterCondition(FilterSpec filter, Map<String, ColumnDef> columns,
            String alias, List<Object[]> extraParams, AtomicInteger paramCounter) {
        ColumnDef colDef = columns.get(filter.column());
        String colExpr = colDef.jpqlExpr();
        if (filter.transform() != null) {
            colExpr = applyTransform(colExpr, filter.transform());
        }
        if ("EXISTS".equals(filter.operator()) || "NOT EXISTS".equals(filter.operator())) {
            String subJpql = buildSubqueryJpql(filter.subquery(), extraParams, paramCounter, 1);
            return filter.operator() + " (" + subJpql + ")";
        } else if (filter.subquery() != null) {
            String subJpql = buildSubqueryJpql(filter.subquery(), extraParams, paramCounter, 1);
            return colExpr + " " + filter.operator() + " (" + subJpql + ")";
        } else if (filter.columnRef() != null) {
            String refExpr = resolveColumnRef(filter.columnRef(), columns, alias);
            if (filter.columnRefTransform() != null) {
                refExpr = applyTransform(refExpr, filter.columnRefTransform());
            }
            return colExpr + " " + filter.operator() + " " + refExpr;
        } else {
            String paramName = "p" + paramCounter.getAndIncrement();
            extraParams.add(new Object[] { paramName, coerce(filter.value(), colDef) });
            return colExpr + " " + filter.operator() + " :" + paramName;
        }
    }

    private void bindParameters(Query query, TableConfig table, Long userId, UUID categoryId,
            List<Object[]> extraParams) {
        if (table.userIdPath() != null) {
            query.setParameter("userId", userId);
        }
        if (categoryId != null && table.categoryIdPath() != null) {
            query.setParameter("categoryId", categoryId);
        }
        if (table.rankedStatusPath() != null) {
            query.setParameter("rankedStatus", MapDifficultyStatus.RANKED);
        }
        for (Object[] binding : extraParams) {
            query.setParameter((String) binding[0], binding[1]);
        }
    }

    private String resolveColumnRef(String ref, Map<String, ColumnDef> columns, String currentAlias) {
        ColumnDef def = columns.get(ref);
        return def != null ? def.jpqlExpr() : ref;
    }

    private String resolveColumnRefInSubquery(String ref, Map<String, ColumnDef> columns,
            String subAlias, int depth) {
        if (ref.startsWith("OUTER.")) {
            String col = ref.substring(6);
            ColumnDef def = columns.get(col);
            if (def == null) return ref;
            String baseAlias = subAlias.substring(0, subAlias.indexOf('_'));
            String outerAlias = depth == 1 ? baseAlias : baseAlias + "_" + (depth - 1);
            return def.jpqlExpr().replace(baseAlias + ".", outerAlias + ".");
        }
        ColumnDef def = columns.get(ref);
        if (def == null) return ref;
        return def.jpqlExpr().replace(
                subAlias.substring(0, subAlias.indexOf('_')) + ".", subAlias + ".");
    }

    private String applyTransform(String colExpr, MilestoneQuerySpec.TransformSpec transform) {
        return switch (transform.function()) {
            case "MOD" -> "MOD(" + colExpr + ", " + transform.argument() + ")";
            case "INTERVAL_SUBTRACT" -> colExpr + " - INTERVAL '" + transform.argument() + "'";
            default -> colExpr;
        };
    }

    private static final Map<String, String> JPQL_TO_SQL = new HashMap<>();
    static {
        JPQL_TO_SQL.put("s.id", "s.id");
        JPQL_TO_SQL.put("s.ap", "s.ap");
        JPQL_TO_SQL.put("s.weightedAp", "s.weighted_ap");
        JPQL_TO_SQL.put("s.score", "s.score");
        JPQL_TO_SQL.put("s.scoreNoMods", "s.score_no_mods");
        JPQL_TO_SQL.put("s.rank", "s.rank");
        JPQL_TO_SQL.put("s.maxCombo", "s.max_combo");
        JPQL_TO_SQL.put("s.misses", "s.misses");
        JPQL_TO_SQL.put("s.badCuts", "s.bad_cuts");
        JPQL_TO_SQL.put("s.wallHits", "s.wall_hits");
        JPQL_TO_SQL.put("s.bombHits", "s.bomb_hits");
        JPQL_TO_SQL.put("s.pauses", "s.pauses");
        JPQL_TO_SQL.put("s.streak115", "s.streak_115");
        JPQL_TO_SQL.put("s.playCount", "s.play_count");
        JPQL_TO_SQL.put("s.active", "s.active");
        JPQL_TO_SQL.put("s.partial", "s.partial");
        JPQL_TO_SQL.put("s.timeSet", "s.time_set");
        JPQL_TO_SQL.put("s.supersedes.id", "s.supersedes_id");
        JPQL_TO_SQL.put("s.supersedesReason", "s.supersedes_reason");
        JPQL_TO_SQL.put("s.supersedes.timeSet", "sup.time_set");
        JPQL_TO_SQL.put("s.user.id", "s.user_id");
        JPQL_TO_SQL.put("s.mapDifficulty", "s.map_difficulty_id");
        JPQL_TO_SQL.put("s.mapDifficulty.id", "s.map_difficulty_id");
        JPQL_TO_SQL.put("s.mapDifficulty.status", "md.status");
        JPQL_TO_SQL.put("s.mapDifficulty.category.id", "md.category_id");
        JPQL_TO_SQL.put("s.mapDifficulty.category.code", "c.code");
        JPQL_TO_SQL.put("s.mapDifficulty.map.mapAuthor", "m.map_author");
        JPQL_TO_SQL.put("s.mapDifficulty.map.songName", "m.song_name");
        JPQL_TO_SQL.put("s.mapDifficulty.map.songHash", "m.song_hash");
        JPQL_TO_SQL.put("s.mapDifficulty.difficulty", "md.difficulty");
        JPQL_TO_SQL.put("s.mapDifficulty.maxScore", "md.max_score");
        JPQL_TO_SQL.put("CAST(s.score AS Double) / s.mapDifficulty.maxScore",
                "CAST(s.score AS DOUBLE PRECISION) / md.max_score");
    }

    private String toNativeSql(String jpqlExpr) {
        return JPQL_TO_SQL.getOrDefault(jpqlExpr, jpqlExpr);
    }

    private BigDecimal evaluateNativeSql(MilestoneQuerySpec spec, Long userId, UUID categoryId) {
        TableConfig table = TABLE_CONFIG.get(spec.from());
        Map<String, ColumnDef> columns = COLUMN_ALLOWLIST.get(spec.from());
        ColumnDef selectCol = columns.get(spec.select().column());
        String fn = spec.select().function().toUpperCase();

        String selectExpr = buildSelectClause(fn, toNativeSql(selectCol.jpqlExpr()));
        String fromClause = buildNativeFromClause(spec, table, columns, selectCol);

        List<String> conditions = new ArrayList<>();
        AtomicInteger paramCounter = new AtomicInteger(0);
        List<Object[]> extraParams = new ArrayList<>();

        if (table.userIdPath() != null) {
            conditions.add(toNativeSql(table.userIdPath()) + " = :userId");
        }
        if (categoryId != null && table.categoryIdPath() != null) {
            conditions.add(toNativeSql(table.categoryIdPath()) + " = :categoryId");
        }
        if (table.rankedStatusPath() != null) {
            conditions.add(toNativeSql(table.rankedStatusPath()) + " = 'ranked'");
        }

        for (FilterSpec filter : spec.filters() != null ? spec.filters() : List.<FilterSpec>of()) {
            conditions.add(buildSingleNativeFilterCondition(filter, columns, table.alias(),
                    extraParams, paramCounter));
        }

        if (spec.orGroups() != null && !spec.orGroups().isEmpty()) {
            List<String> orBranches = new ArrayList<>();
            for (List<FilterSpec> group : spec.orGroups()) {
                List<String> groupConditions = new ArrayList<>();
                for (FilterSpec filter : group) {
                    groupConditions.add(buildSingleNativeFilterCondition(filter, columns, table.alias(),
                            extraParams, paramCounter));
                }
                orBranches.add("(" + String.join(" AND ", groupConditions) + ")");
            }
            conditions.add("(" + String.join(" OR ", orBranches) + ")");
        }

        boolean hasGroupBy = spec.groupBy() != null && !spec.groupBy().isEmpty();
        StringBuilder sql = new StringBuilder();

        if (hasGroupBy) {
            String outerFn = spec.outerFunction().toUpperCase();
            List<String> groupByExprs = spec.groupBy().stream().map(gb -> {
                String expr = toNativeSql(columns.get(gb.column()).jpqlExpr());
                return gb.cast() != null && "DATE".equalsIgnoreCase(gb.cast())
                        ? "CAST(" + expr + " AS DATE)"
                        : expr;
            }).toList();

            sql.append("SELECT COALESCE(").append(outerFn).append("(agg), 0) FROM (")
                    .append("SELECT ").append(selectExpr).append(" as agg FROM ").append(fromClause);
            if (!conditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", conditions));
            }
            sql.append(" GROUP BY ").append(String.join(", ", groupByExprs)).append(") sub");
        } else if (spec.orderBy() != null && !spec.orderBy().isEmpty() && spec.limit() != null) {
            String innerCol = toNativeSql(selectCol.jpqlExpr());
            String outerAgg = buildSelectClause(fn, "sub.val");
            sql.append("SELECT ").append(outerAgg).append(" FROM (")
                    .append("SELECT ").append(innerCol).append(" as val FROM ").append(fromClause);
            if (!conditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", conditions));
            }
            sql.append(" ORDER BY ");
            sql.append(spec.orderBy().stream().map(ob -> {
                String expr = toNativeSql(columns.get(ob.column()).jpqlExpr());
                return expr + (ob.direction() != null ? " " + ob.direction().toUpperCase() : "");
            }).collect(java.util.stream.Collectors.joining(", ")));
            sql.append(" LIMIT ").append(spec.limit());
            sql.append(") sub");
        } else {
            sql.append("SELECT ").append(selectExpr).append(" FROM ").append(fromClause);
            if (!conditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", conditions));
            }
        }

        Query query = entityManager.createNativeQuery(sql.toString());
        if (table.userIdPath() != null) {
            query.setParameter("userId", userId);
        }
        if (categoryId != null && table.categoryIdPath() != null) {
            query.setParameter("categoryId", categoryId);
        }
        for (Object[] binding : extraParams) {
            query.setParameter((String) binding[0], binding[1]);
        }
        return toBigDecimal(query.getSingleResult());
    }

    private String buildSingleNativeFilterCondition(FilterSpec filter, Map<String, ColumnDef> columns,
            String alias, List<Object[]> extraParams, AtomicInteger paramCounter) {
        ColumnDef colDef = columns.get(filter.column());
        String colExpr = toNativeSql(colDef.jpqlExpr());
        if (filter.transform() != null) {
            colExpr = applyTransform(colExpr, filter.transform());
        }
        if ("EXISTS".equals(filter.operator()) || "NOT EXISTS".equals(filter.operator())) {
            String subSql = buildNativeSubquery(filter.subquery(), columns, alias,
                    extraParams, paramCounter);
            return filter.operator() + " (" + subSql + ")";
        } else if (filter.columnRef() != null) {
            String refExpr = resolveNativeColumnRef(filter.columnRef(), columns, alias);
            if (filter.columnRefTransform() != null) {
                refExpr = applyTransform(refExpr, filter.columnRefTransform());
            }
            return colExpr + " " + filter.operator() + " " + refExpr;
        } else {
            String paramName = "p" + paramCounter.getAndIncrement();
            extraParams.add(new Object[] { paramName, coerce(filter.value(), colDef) });
            return colExpr + " " + filter.operator() + " :" + paramName;
        }
    }

    private String resolveNativeColumnRef(String ref, Map<String, ColumnDef> columns, String alias) {
        if (ref.startsWith("OUTER.")) {
            String col = ref.substring(6);
            ColumnDef def = columns.get(col);
            return def != null ? toNativeSql(def.jpqlExpr()) : ref;
        }
        ColumnDef def = columns.get(ref);
        return def != null ? toNativeSql(def.jpqlExpr()) : ref;
    }

    private String buildNativeSubquery(MilestoneQuerySpec spec, Map<String, ColumnDef> outerColumns,
            String outerAlias, List<Object[]> extraParams, AtomicInteger paramCounter) {
        TableConfig table = TABLE_CONFIG.get(spec.from());
        Map<String, ColumnDef> columns = COLUMN_ALLOWLIST.get(spec.from());
        ColumnDef selectCol = columns.get(spec.select().column());
        String fn = spec.select().function().toUpperCase();

        String subAlias = table.alias() + "2";
        String selectExpr = buildSelectClause(fn, toNativeSql(selectCol.jpqlExpr()).replace(table.alias() + ".", subAlias + "."));

        StringBuilder from = new StringBuilder(spec.from()).append(" ").append(subAlias);
        if ("scores".equals(spec.from())) {
            from.append(" JOIN map_difficulties md2 ON ").append(subAlias).append(".map_difficulty_id = md2.id");
        }

        List<String> conditions = new ArrayList<>();
        if (table.userIdPath() != null) {
            conditions.add(toNativeSql(table.userIdPath()).replace(table.alias() + ".", subAlias + ".") + " = :userId");
        }
        if (table.rankedStatusPath() != null) {
            String statusExpr = toNativeSql(table.rankedStatusPath()).replace("md.", "md2.");
            conditions.add(statusExpr + " = 'ranked'");
        }

        for (FilterSpec filter : spec.filters() != null ? spec.filters() : List.<FilterSpec>of()) {
            ColumnDef colDef = columns.get(filter.column());
            String colExpr = toNativeSql(colDef.jpqlExpr()).replace(table.alias() + ".", subAlias + ".").replace("md.", "md2.");
            if (filter.transform() != null) {
                colExpr = applyTransform(colExpr, filter.transform());
            }
            if (filter.columnRef() != null) {
                String ref = filter.columnRef();
                String refExpr;
                if (ref.startsWith("OUTER.")) {
                    String col = ref.substring(6);
                    ColumnDef refDef = outerColumns.get(col);
                    refExpr = refDef != null ? toNativeSql(refDef.jpqlExpr()) : ref;
                } else {
                    ColumnDef refDef = columns.get(ref);
                    refExpr = refDef != null ? toNativeSql(refDef.jpqlExpr()).replace(table.alias() + ".", subAlias + ".") : ref;
                }
                if (filter.columnRefTransform() != null) {
                    refExpr = applyTransform(refExpr, filter.columnRefTransform());
                }
                conditions.add(colExpr + " " + filter.operator() + " " + refExpr);
            } else {
                String paramName = "p" + paramCounter.getAndIncrement();
                conditions.add(colExpr + " " + filter.operator() + " :" + paramName);
                extraParams.add(new Object[] { paramName, coerce(filter.value(), colDef) });
            }
        }

        StringBuilder sb = new StringBuilder()
                .append("SELECT ").append(selectExpr).append(" FROM ").append(from);
        if (!conditions.isEmpty()) {
            sb.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        return sb.toString();
    }

    private String buildNativeFromClause(MilestoneQuerySpec spec, TableConfig table,
            Map<String, ColumnDef> columns, ColumnDef selectCol) {
        if (!"scores".equals(spec.from())) {
            return spec.from() + " " + table.alias();
        }
        StringBuilder from = new StringBuilder("scores s");

        boolean needsMd = false;
        boolean needsMap = false;
        boolean needsCategory = false;
        boolean needsSupersedes = false;

        List<String> allJpqlExprs = new ArrayList<>();
        allJpqlExprs.add(selectCol.jpqlExpr());
        if (table.rankedStatusPath() != null)
            allJpqlExprs.add(table.rankedStatusPath());
        if (table.categoryIdPath() != null)
            allJpqlExprs.add(table.categoryIdPath());
        for (FilterSpec f : spec.filters() != null ? spec.filters() : List.<FilterSpec>of()) {
            allJpqlExprs.add(columns.get(f.column()).jpqlExpr());
        }
        if (spec.groupBy() != null) {
            for (var gb : spec.groupBy()) {
                allJpqlExprs.add(columns.get(gb.column()).jpqlExpr());
            }
        }

        for (String expr : allJpqlExprs) {
            if (expr.contains("mapDifficulty"))
                needsMd = true;
            if (expr.contains(".map."))
                needsMap = true;
            if (expr.contains(".category."))
                needsCategory = true;
            if (expr.contains("supersedes"))
                needsSupersedes = true;
        }

        if (needsMd || needsMap || needsCategory) {
            from.append(" JOIN map_difficulties md ON s.map_difficulty_id = md.id");
        }
        if (needsMap)
            from.append(" JOIN maps m ON md.map_id = m.id");
        if (needsCategory)
            from.append(" JOIN categories c ON md.category_id = c.id");
        if (needsSupersedes)
            from.append(" JOIN scores sup ON s.supersedes_id = sup.id");

        return from.toString();
    }

    public Score findQualifyingScore(MilestoneQuerySpec spec, Long userId, UUID categoryId,
            BigDecimal targetValue, String comparison) {
        if (!"scores".equals(spec.from()))
            return null;

        TableConfig table = TABLE_CONFIG.get(spec.from());
        Map<String, ColumnDef> columns = COLUMN_ALLOWLIST.get(spec.from());
        String fn = spec.select().function().toUpperCase();
        ColumnDef selectCol = columns.get(spec.select().column());

        AtomicInteger paramCounter = new AtomicInteger(0);
        List<Object[]> extraParams = new ArrayList<>();
        List<String> conditions = buildConditions(spec, table, columns, userId, categoryId,
                extraParams, paramCounter);

        boolean isMaxMin = "MAX".equals(fn) || "MIN".equals(fn);
        if (isMaxMin) {
            String op = "LTE".equals(comparison) ? " <= " : " >= ";
            String paramName = "target" + paramCounter.getAndIncrement();
            conditions.add(selectCol.jpqlExpr() + op + ":" + paramName);
            extraParams.add(new Object[] { paramName, targetValue });
        }

        String jpql = "SELECT s FROM Score s WHERE " + String.join(" AND ", conditions)
                + " ORDER BY s.timeSet ASC";

        Query query = entityManager.createQuery(jpql, Score.class);
        bindParameters(query, table, userId, categoryId, extraParams);
        query.setMaxResults(1);

        @SuppressWarnings("unchecked")
        List<Score> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private record BatchKey(String fromTable, UUID categoryId, String filterSignature) {
    }

    private record SelectExpr(String function, String column) {
    }

    public Map<UUID, BigDecimal> evaluateBatch(List<Milestone> milestones, Long userId) {
        if (milestones.isEmpty())
            return Map.of();
        if (milestones.size() == 1) {
            Milestone m = milestones.get(0);
            UUID catId = m.getCategory() != null ? m.getCategory().getId() : null;
            BigDecimal result = evaluate(m.getQuerySpec(), userId, catId);
            Map<UUID, BigDecimal> singleResult = new HashMap<>();
            singleResult.put(m.getId(), result);
            return singleResult;
        }

        Map<BatchKey, List<Milestone>> groups = new LinkedHashMap<>();
        for (Milestone m : milestones) {
            MilestoneQuerySpec qs = m.getQuerySpec();
            UUID catId = m.getCategory() != null ? m.getCategory().getId() : null;
            String filterSig = computeFilterSignature(qs.filters());
            if (qs.having() != null) {
                filterSig += "|HAVING:" + qs.having().function() + "," + qs.having().column() + ","
                        + qs.having().operator() + "," + qs.having().value();
            }
            if (qs.orGroups() != null && !qs.orGroups().isEmpty()) {
                filterSig += "|OR:" + qs.orGroups().stream()
                        .map(group -> computeFilterSignature(group))
                        .collect(java.util.stream.Collectors.joining("|"));
            }
            if (qs.divisor() != null) {
                filterSig += "|DIV:" + qs.divisor().from() + "," + qs.divisor().select().function()
                        + "," + qs.divisor().select().column()
                        + "," + computeFilterSignature(qs.divisor().filters());
            }
            if (qs.groupBy() != null && !qs.groupBy().isEmpty()) {
                filterSig += "|GB:" + qs.groupBy().stream()
                        .map(gb -> gb.column() + (gb.cast() != null ? ":" + gb.cast() : ""))
                        .collect(java.util.stream.Collectors.joining(","))
                        + "|OF:" + qs.outerFunction();
            }
            BatchKey key = new BatchKey(qs.from(), catId, filterSig);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }

        Map<UUID, BigDecimal> results = new HashMap<>();
        for (var entry : groups.entrySet()) {
            results.putAll(evaluateGroup(entry.getKey(), entry.getValue(), userId));
        }
        return results;
    }

    private Map<UUID, BigDecimal> evaluateGroup(BatchKey key, List<Milestone> milestones, Long userId) {
        TableConfig table = TABLE_CONFIG.get(key.fromTable());
        Map<String, ColumnDef> columns = COLUMN_ALLOWLIST.get(key.fromTable());

        Map<SelectExpr, List<UUID>> selectToMilestoneIds = new LinkedHashMap<>();
        for (Milestone m : milestones) {
            SelectExpr expr = new SelectExpr(
                    m.getQuerySpec().select().function().toUpperCase(),
                    m.getQuerySpec().select().column());
            selectToMilestoneIds.computeIfAbsent(expr, k -> new ArrayList<>()).add(m.getId());
        }

        List<SelectExpr> orderedSelects = new ArrayList<>(selectToMilestoneIds.keySet());
        List<String> selectClauses = new ArrayList<>();
        for (SelectExpr expr : orderedSelects) {
            ColumnDef colDef = columns.get(expr.column());
            selectClauses.add(buildSelectClause(expr.function(), colDef.jpqlExpr()));
        }

        MilestoneQuerySpec referenceSpec = milestones.get(0).getQuerySpec();

        AtomicInteger paramCounter = new AtomicInteger(0);
        List<Object[]> extraParams = new ArrayList<>();
        List<String> conditions = buildConditions(referenceSpec, table, columns, userId, key.categoryId(),
                extraParams, paramCounter);

        StringBuilder jpql = new StringBuilder()
                .append("SELECT ").append(String.join(", ", selectClauses))
                .append(" FROM ").append(table.entity()).append(" ").append(table.alias());
        if (!conditions.isEmpty()) {
            jpql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        Query query = entityManager.createQuery(jpql.toString());
        bindParameters(query, table, userId, key.categoryId(), extraParams);

        Object rawResult = query.getSingleResult();

        Map<UUID, BigDecimal> results = new HashMap<>();
        if (orderedSelects.size() == 1) {
            BigDecimal value = toBigDecimal(rawResult);
            for (UUID milestoneId : selectToMilestoneIds.get(orderedSelects.get(0))) {
                results.put(milestoneId, value);
            }
        } else {
            Object[] row = (Object[]) rawResult;
            for (int i = 0; i < orderedSelects.size(); i++) {
                BigDecimal value = toBigDecimal(row[i]);
                for (UUID milestoneId : selectToMilestoneIds.get(orderedSelects.get(i))) {
                    results.put(milestoneId, value);
                }
            }
        }
        return results;
    }

    private String computeFilterSignature(List<FilterSpec> filters) {
        if (filters == null || filters.isEmpty())
            return "";
        List<FilterSpec> sorted = filters.stream()
                .sorted(Comparator.comparing(FilterSpec::column))
                .toList();
        StringBuilder sb = new StringBuilder();
        for (FilterSpec f : sorted) {
            sb.append(f.column()).append('|').append(f.operator()).append('|');
            if (f.subquery() != null) {
                sb.append("SUB(").append(f.subquery().from()).append(',')
                        .append(f.subquery().select().function()).append(',')
                        .append(f.subquery().select().column()).append(',')
                        .append(computeFilterSignature(f.subquery().filters()))
                        .append(')');
            } else {
                sb.append(f.value());
            }
            sb.append(';');
        }
        return sb.toString();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null)
            return null;
        if (value instanceof BigDecimal bd)
            return bd;
        if (value instanceof Number n)
            return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }

    private String buildSelectClause(String fn, String expr) {
        return switch (fn) {
            case "COUNT_DISTINCT" -> "COUNT(DISTINCT " + expr + ")";
            case "PLAIN" -> expr;
            default -> fn + "(" + expr + ")";
        };
    }

    private String buildSubqueryJpql(MilestoneQuerySpec spec, List<Object[]> extraParams,
            AtomicInteger paramCounter, int depth) {
        TableConfig table = TABLE_CONFIG.get(spec.from());
        Map<String, ColumnDef> columns = COLUMN_ALLOWLIST.get(spec.from());
        String alias = table.alias() + "_" + depth;
        Function<String, String> rewrite = expr -> expr.replace(table.alias() + ".", alias + ".");

        ColumnDef selectCol = columns.get(spec.select().column());
        String fn = spec.select().function().toUpperCase();
        String selectClause = buildSelectClause(fn, rewrite.apply(selectCol.jpqlExpr()));

        List<String> conditions = new ArrayList<>();
        if ("users".equals(spec.from()) && table.userIdPath() != null) {
            conditions.add(rewrite.apply(table.userIdPath()) + " = :userId");
        }
        if (table.rankedStatusPath() != null) {
            conditions.add(rewrite.apply(table.rankedStatusPath()) + " = :rankedStatus");
        }

        List<FilterSpec> filters = spec.filters() != null ? spec.filters() : List.of();
        for (FilterSpec filter : filters) {
            ColumnDef colDef = columns.get(filter.column());
            String colExpr = rewrite.apply(colDef.jpqlExpr());
            if (filter.transform() != null) {
                colExpr = applyTransform(colExpr, filter.transform());
            }
            if ("EXISTS".equals(filter.operator()) || "NOT EXISTS".equals(filter.operator())) {
                String innerJpql = buildSubqueryJpql(filter.subquery(), extraParams, paramCounter, depth + 1);
                conditions.add(filter.operator() + " (" + innerJpql + ")");
            } else if (filter.subquery() != null) {
                String innerJpql = buildSubqueryJpql(filter.subquery(), extraParams, paramCounter, depth + 1);
                conditions.add(colExpr + " " + filter.operator() + " (" + innerJpql + ")");
            } else if (filter.columnRef() != null) {
                String refExpr = resolveColumnRefInSubquery(filter.columnRef(), columns, alias, depth);
                if (filter.columnRefTransform() != null) {
                    refExpr = applyTransform(refExpr, filter.columnRefTransform());
                }
                conditions.add(colExpr + " " + filter.operator() + " " + refExpr);
            } else {
                String paramName = "p" + paramCounter.getAndIncrement();
                conditions.add(colExpr + " " + filter.operator() + " :" + paramName);
                extraParams.add(new Object[] { paramName, coerce(filter.value(), colDef) });
            }
        }

        StringBuilder sb = new StringBuilder()
                .append("SELECT ").append(selectClause)
                .append(" FROM ").append(table.entity()).append(" ").append(alias);
        if (!conditions.isEmpty()) {
            sb.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        return sb.toString();
    }

    public MilestoneSchemaResponse getSchema() {
        Map<String, List<ColumnInfo>> tableColumns = new LinkedHashMap<>();
        COLUMN_ALLOWLIST.forEach((table, cols) -> {
            List<ColumnInfo> infos = cols.entrySet().stream().map(e -> {
                ColumnDef def = e.getValue();
                List<String> enumValues = null;
                if (def.type() == ValueType.ENUM && def.enumClass() != null) {
                    enumValues = Arrays.stream(def.enumClass().getEnumConstants())
                            .map(Enum::name)
                            .toList();
                }
                return new ColumnInfo(e.getKey(), def.type().name().toLowerCase(), enumValues);
            }).toList();
            tableColumns.put(table, infos);
        });
        return new MilestoneSchemaResponse(
                tableColumns,
                AGGREGATE_FUNCTIONS.stream().sorted().toList(),
                OPERATORS.stream().sorted().toList());
    }
}
