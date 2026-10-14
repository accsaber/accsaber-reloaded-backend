package com.accsaber.backend.service.milestone.sql;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.FilterSpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.GroupBySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.HavingSpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.OrderBySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.SelectSpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.TransformSpec;
import com.accsaber.backend.service.milestone.source.MilestoneSource;
import com.accsaber.backend.service.milestone.source.MilestoneSourceColumn;
import com.accsaber.backend.service.milestone.source.MilestoneSourceRegistry;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MilestoneSqlCompiler {

    private static final Pattern INTERVAL_ARGUMENT = Pattern.compile(
            "^\\d{1,9} (second|minute|hour|day|week|month|year)s?$");
    private static final String OUTER_PREFIX = "OUTER.";
    private static final Set<String> NULL_OPERATORS = Set.of("IS NULL", "IS NOT NULL");

    private final MilestoneSourceRegistry registry;

    public record Compiled(String sql, Map<String, Object> params) {
    }

    public record Context(Long userId, UUID categoryId, Double havingValue) {

        public static Context of(Long userId, UUID categoryId) {
            return new Context(userId, categoryId, null);
        }

        public Context withHavingValue(Double value) {
            return new Context(userId, categoryId, value);
        }
    }

    public Compiled scalar(MilestoneQuerySpec spec, Context context) {
        return compile(List.of(spec.select()), spec, context);
    }

    public Compiled multi(List<SelectSpec> selects, MilestoneQuerySpec reference, Context context) {
        return compile(selects, reference, context);
    }

    private Compiled compile(List<SelectSpec> selects, MilestoneQuerySpec spec, Context context) {
        Scope scope = new Scope(source(spec.from()), "", null, new LinkedHashMap<>(),
                new AtomicInteger(), new AtomicInteger());
        List<String> conditions = conditions(spec, scope, context, true, true);

        List<String> projections = new ArrayList<>();
        for (SelectSpec select : selects) {
            projections.add(projection(select, spec, scope, context));
        }

        boolean grouped = spec.groupBy() != null && !spec.groupBy().isEmpty();
        boolean limited = spec.orderBy() != null && !spec.orderBy().isEmpty() && spec.limit() != null;

        String sql;
        if (grouped) {
            sql = grouped(projections, spec, scope, conditions);
        } else if (limited) {
            sql = limited(selects, spec, scope, conditions);
        } else {
            sql = "SELECT " + String.join(", ", projections) + " FROM " + scope.from()
                    + where(conditions);
        }
        return new Compiled(sql, scope.params);
    }

    private String grouped(List<String> projections, MilestoneQuerySpec spec, Scope scope,
            List<String> conditions) {
        List<String> keys = new ArrayList<>();
        for (GroupBySpec groupBy : spec.groupBy()) {
            String expr = scope.column(groupBy.column());
            keys.add(groupBy.cast() != null ? "CAST(" + expr + " AS DATE)" : expr);
        }
        String inner = "SELECT " + projections.get(0) + " AS agg FROM " + scope.from()
                + where(conditions) + " GROUP BY " + String.join(", ", keys);
        return "SELECT COALESCE(" + aggregate(spec.outerFunction()) + "(agg), 0) FROM (" + inner + ") grouped";
    }

    private String limited(List<SelectSpec> selects, MilestoneQuerySpec spec, Scope scope,
            List<String> conditions) {
        SelectSpec select = selects.get(0);
        String order = spec.orderBy().stream()
                .map(o -> scope.column(o.column()) + direction(o))
                .collect(Collectors.joining(", "));
        String inner = "SELECT " + scope.column(select.column()) + " AS val FROM " + scope.from()
                + where(conditions) + " ORDER BY " + order + " LIMIT " + spec.limit();
        return "SELECT " + apply(select.function(), "ranked.val") + " FROM (" + inner + ") ranked";
    }

    private String direction(OrderBySpec order) {
        return order.direction() == null ? "" : " " + order.direction().toUpperCase();
    }

    private String projection(SelectSpec select, MilestoneQuerySpec spec, Scope scope, Context context) {
        String expr = apply(select.function(), scope.column(select.column()));
        if (spec.having() != null) {
            HavingSpec having = spec.having();
            String gate = apply(having.function(), scope.column(having.column()));
            Object value = having.valueQuery() != null
                    ? nullToZero(context.havingValue())
                    : coerce(having.value(), scope.definition(having.column()));
            expr = "CASE WHEN " + gate + " " + having.operator() + " " + scope.bind(value)
                    + " THEN " + expr + " ELSE NULL END";
        }
        return offset(expr, select.offset());
    }

    private Object nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private String offset(String expr, Integer offset) {
        if (offset == null || offset == 0) {
            return expr;
        }
        return "(" + expr + (offset > 0 ? " + " + offset : " - " + (-offset)) + ")";
    }

    private List<String> conditions(MilestoneQuerySpec spec, Scope scope, Context context,
            boolean applyUser, boolean applyCategory) {
        List<String> conditions = new ArrayList<>();
        MilestoneSource source = scope.source;

        if (applyUser && source.userTemplate() != null) {
            if (isCountryScoped(spec)) {
                conditions.add(scope.render(source.countryTemplate())
                        + " = (SELECT country FROM users WHERE id = "
                        + scope.bindNamed("userId", context.userId()) + ")");
            } else {
                conditions.add(scope.render(source.userTemplate()) + " = "
                        + scope.bindNamed("userId", context.userId()));
            }
        }
        if (applyCategory && context.categoryId() != null && source.categoryTemplate() != null) {
            conditions.add(scope.render(source.categoryTemplate()) + " = "
                    + scope.bindNamed("categoryId", context.categoryId()));
        }
        for (String filter : source.implicitFilters()) {
            conditions.add(scope.render(filter));
        }
        for (FilterSpec filter : spec.filters() != null ? spec.filters() : List.<FilterSpec>of()) {
            conditions.add(condition(filter, scope, context));
        }
        if (spec.orGroups() != null && !spec.orGroups().isEmpty()) {
            List<String> branches = new ArrayList<>();
            for (List<FilterSpec> group : spec.orGroups()) {
                List<String> parts = new ArrayList<>();
                for (FilterSpec filter : group) {
                    parts.add(condition(filter, scope, context));
                }
                branches.add("(" + String.join(" AND ", parts) + ")");
            }
            conditions.add("(" + String.join(" OR ", branches) + ")");
        }
        return conditions;
    }

    private String condition(FilterSpec filter, Scope scope, Context context) {
        String operator = filter.operator();
        if ("EXISTS".equals(operator) || "NOT EXISTS".equals(operator)) {
            return operator + " (" + subquery(filter.subquery(), scope, context) + ")";
        }
        String left = transform(scope.column(filter.column()), filter.transform());
        if (NULL_OPERATORS.contains(operator)) {
            return left + " " + operator;
        }
        if (filter.subquery() != null) {
            return left + " " + operator + " (" + subquery(filter.subquery(), scope, context) + ")";
        }
        if (filter.columnRef() != null) {
            return left + " " + operator + " "
                    + transform(scope.reference(filter.columnRef()), filter.columnRefTransform());
        }
        return left + " " + operator + " " + scope.bind(coerce(filter.value(), scope.definition(filter.column())));
    }

    private String subquery(MilestoneQuerySpec spec, Scope parent, Context context) {
        Scope scope = parent.child(source(spec.from()));
        boolean userScoped = "users".equals(spec.from()) || "USER".equalsIgnoreCase(spec.scope());
        List<String> conditions = conditions(spec, scope, context, userScoped, false);
        return "SELECT " + apply(spec.select().function(), scope.column(spec.select().column()))
                + " FROM " + scope.from() + where(conditions);
    }

    private String where(List<String> conditions) {
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private String transform(String expr, TransformSpec spec) {
        if (spec == null) {
            return expr;
        }
        String argument = String.valueOf(spec.argument()).trim();
        return switch (spec.function()) {
            case "MOD" -> "MOD(" + expr + ", " + modArgument(argument) + ")";
            case "INTERVAL_SUBTRACT" -> {
                if (!INTERVAL_ARGUMENT.matcher(argument).matches()) {
                    throw new ValidationException(
                            "INTERVAL_SUBTRACT argument must look like '<n> <unit>' (e.g. '7 days')");
                }
                yield expr + " - INTERVAL '" + argument + "'";
            }
            default -> throw new ValidationException("Unsupported transform function: " + spec.function());
        };
    }

    public long modArgument(String argument) {
        try {
            long value = Long.parseLong(argument);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ValidationException("MOD transform argument must be a positive integer");
        }
    }

    private String apply(String function, String expr) {
        return switch (aggregate(function)) {
            case "COUNT_DISTINCT" -> "COUNT(DISTINCT " + expr + ")";
            case "PLAIN" -> expr;
            default -> aggregate(function) + "(" + expr + ")";
        };
    }

    private String aggregate(String function) {
        return function == null ? null : function.toUpperCase();
    }

    public static boolean isCountryScoped(MilestoneQuerySpec spec) {
        return spec.scope() != null && "COUNTRY".equalsIgnoreCase(spec.scope());
    }

    private MilestoneSource source(String name) {
        MilestoneSource source = registry.get(name);
        if (source == null) {
            throw new ValidationException("Unsupported table: " + name + ". Allowed: " + registry.names());
        }
        return source;
    }

    private Object coerce(Object value, MilestoneSourceColumn column) {
        if (value == null) {
            throw new ValidationException("Filter value cannot be null");
        }
        try {
            return switch (column.type()) {
                case STRING -> value.toString();
                case INTEGER -> intValue(value);
                case LONG -> wholeNumber(value);
                case DOUBLE -> value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
                case BOOLEAN -> value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
                case INSTANT -> value instanceof Instant i ? i : Instant.parse(value.toString());
                case UUID -> value instanceof UUID u ? u : UUID.fromString(value.toString());
                case ENUM -> enumDatabaseValue(column.enumClass(), value.toString());
            };
        } catch (java.time.format.DateTimeParseException | IllegalArgumentException e) {
            throw new ValidationException(
                    "Invalid value '" + value + "' for type " + column.type() + ": " + e.getMessage());
        }
    }

    private long wholeNumber(Object value) {
        if (!(value instanceof Number number)) {
            return Long.parseLong(value.toString().trim());
        }
        double raw = number.doubleValue();
        if (Double.isNaN(raw) || Double.isInfinite(raw) || raw != Math.rint(raw)) {
            throw new NumberFormatException("expected a whole number but got " + value);
        }
        return number.longValue();
    }

    private int intValue(Object value) {
        long whole = wholeNumber(value);
        if (whole < Integer.MIN_VALUE || whole > Integer.MAX_VALUE) {
            throw new NumberFormatException("value out of INTEGER range: " + value);
        }
        return (int) whole;
    }

    private String enumDatabaseValue(Class<? extends Enum<?>> enumClass, String raw) {
        Enum<?> constant = constantNamed(enumClass, raw);
        if (constant == null) {
            constant = fromDatabaseValue(enumClass, raw);
        }
        try {
            return (String) constant.getClass().getMethod("getDbValue").invoke(constant);
        } catch (ReflectiveOperationException noDbValue) {
            return constant.name();
        }
    }

    private Enum<?> constantNamed(Class<? extends Enum<?>> enumClass, String raw) {
        for (Enum<?> constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(raw)) {
                return constant;
            }
        }
        return null;
    }

    private Enum<?> fromDatabaseValue(Class<? extends Enum<?>> enumClass, String raw) {
        for (Enum<?> constant : enumClass.getEnumConstants()) {
            try {
                if (raw.equals(constant.getClass().getMethod("getDbValue").invoke(constant))) {
                    return constant;
                }
            } catch (ReflectiveOperationException noDbValue) {
                break;
            }
        }
        throw new IllegalArgumentException("No enum constant " + enumClass.getSimpleName() + " for '" + raw + "'");
    }

    private final class Scope {

        private final MilestoneSource source;
        private final String suffix;
        private final Scope parent;
        private final Map<String, Object> params;
        private final AtomicInteger counter;
        private final AtomicInteger aliasCounter;
        private final Set<String> joins = new LinkedHashSet<>();

        private Scope(MilestoneSource source, String suffix, Scope parent, Map<String, Object> params,
                AtomicInteger counter, AtomicInteger aliasCounter) {
            this.source = source;
            this.suffix = suffix;
            this.parent = parent;
            this.params = params;
            this.counter = counter;
            this.aliasCounter = aliasCounter;
        }

        private Scope child(MilestoneSource childSource) {
            return new Scope(childSource, "_" + aliasCounter.incrementAndGet(), this, params, counter,
                    aliasCounter);
        }

        private String alias(String key) {
            return (MilestoneSource.BASE.equals(key) ? source.baseAlias() : key) + suffix;
        }

        private String render(String template) {
            String rendered = template;
            for (String placeholder : MilestoneSource.placeholders(template)) {
                if (!MilestoneSource.BASE.equals(placeholder)) {
                    require(placeholder);
                }
                rendered = rendered.replace("{" + placeholder + "}", alias(placeholder));
            }
            return rendered;
        }

        private void require(String key) {
            if (joins.contains(key)) {
                return;
            }
            String template = source.joinTemplate(key);
            if (template == null) {
                throw new ValidationException(
                        "Milestone source '" + source.name() + "' has no join '" + key + "'");
            }
            for (String dependency : MilestoneSource.placeholders(template)) {
                if (!MilestoneSource.BASE.equals(dependency) && !dependency.equals(key)) {
                    require(dependency);
                }
            }
            joins.add(key);
        }

        private MilestoneSourceColumn definition(String key) {
            MilestoneSourceColumn column = source.column(key);
            if (column == null) {
                throw new ValidationException(
                        "Unsupported column '" + key + "' for table '" + source.name() + "'");
            }
            return column;
        }

        private String column(String key) {
            return render(definition(key).template());
        }

        private String reference(String ref) {
            if (!ref.startsWith(OUTER_PREFIX)) {
                return column(ref);
            }
            Scope target = parent != null ? parent : this;
            return target.column(ref.substring(OUTER_PREFIX.length()));
        }

        private String bindNamed(String name, Object value) {
            params.put(name, value);
            return ":" + name;
        }

        private String bind(Object value) {
            String name = "p" + counter.getAndIncrement();
            params.put(name, value);
            return ":" + name;
        }

        private String from() {
            StringBuilder from = new StringBuilder(source.table()).append(' ').append(alias(MilestoneSource.BASE));
            for (String key : List.copyOf(joins)) {
                from.append(' ').append(substitute(source.joinTemplate(key)));
            }
            return from.toString();
        }

        private String substitute(String template) {
            String rendered = template;
            for (String placeholder : MilestoneSource.placeholders(template)) {
                rendered = rendered.replace("{" + placeholder + "}", alias(placeholder));
            }
            return rendered;
        }
    }
}
