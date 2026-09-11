package com.accsaber.backend.service.milestone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.FilterSpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.GroupBySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.HavingSpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.OrderBySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.SelectSpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.TransformSpec;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSchemaResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSchemaResponse.ColumnInfo;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.service.milestone.source.MilestoneSource;
import com.accsaber.backend.service.milestone.source.MilestoneSourceColumn;
import com.accsaber.backend.service.milestone.source.MilestoneSourceRegistry;
import com.accsaber.backend.service.milestone.source.MilestoneValueType;
import com.accsaber.backend.service.milestone.sql.MilestoneSqlCompiler;
import com.accsaber.backend.service.milestone.sql.MilestoneSqlCompiler.Compiled;
import com.accsaber.backend.service.milestone.sql.MilestoneSqlCompiler.Context;
import com.accsaber.backend.util.Rounding;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MilestoneQueryBuilderService {

    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of("COUNT", "COUNT_DISTINCT", "MAX", "MIN", "SUM",
            "AVG", "PLAIN");
    private static final Set<String> OPERATORS = Set.of(">", ">=", "<", "<=", "=", "!=");
    private static final Set<String> SUBQUERY_OPERATORS = Set.of("IN", "NOT IN", "EXISTS", "NOT EXISTS");
    private static final Set<String> NULL_OPERATORS = Set.of("IS NULL", "IS NOT NULL");
    private static final Set<String> TRANSFORM_FUNCTIONS = Set.of("MOD", "INTERVAL_SUBTRACT");
    private static final Set<String> SCOPES = Set.of("USER", "COUNTRY");

    private final EntityManager entityManager;
    private final MilestoneSourceRegistry registry;
    private final MilestoneSqlCompiler compiler;

    public record Progress(Double value, Double gateFraction) {
    }

    public void validate(MilestoneQuerySpec spec) {
        validateSpec(spec, null);
    }

    public Double evaluate(MilestoneQuerySpec spec, Long userId, UUID categoryId) {
        return value(spec, userId, categoryId, havingValue(spec, userId, categoryId));
    }

    public Progress evaluateProgress(MilestoneQuerySpec spec, Long userId, UUID categoryId) {
        if (spec == null) {
            return new Progress(null, null);
        }
        Double havingValue = havingValue(spec, userId, categoryId);
        return new Progress(value(spec, userId, categoryId, havingValue),
                gateFraction(spec, userId, categoryId, havingValue));
    }

    private Double evaluateGate(MilestoneQuerySpec spec, Long userId, UUID categoryId) {
        HavingSpec having = spec.having();
        MilestoneQuerySpec gateSpec = new MilestoneQuerySpec(
                new SelectSpec(having.function(), having.column()),
                spec.from(), spec.filters(), null, null, spec.groupBy(), spec.outerFunction(),
                spec.orderBy(), spec.limit(), spec.orGroups(), spec.scope());
        return evaluateSingle(gateSpec, userId, categoryId);
    }

    private Double gateFraction(MilestoneQuerySpec spec, Long userId, UUID categoryId, Double havingValue) {
        if (spec.having() == null) {
            return null;
        }
        HavingSpec having = spec.having();
        Double target = having.valueQuery() != null
                ? havingValue
                : (having.value() instanceof Number number ? number.doubleValue() : null);
        if (target == null || target <= 0) {
            return null;
        }
        Double gate = evaluateGate(spec, userId, categoryId);
        return gate == null ? 0.0 : Rounding.round(gate / target, 6);
    }

    private Double value(MilestoneQuerySpec spec, Long userId, UUID categoryId, Double havingValue) {
        Double result = evaluateSingle(spec, userId, categoryId, havingValue);
        if (spec.divisor() == null) {
            return result;
        }
        Double divisor = evaluateSingle(spec.divisor(), userId, categoryId);
        if (result == null || divisor == null || divisor == 0.0) {
            return null;
        }
        return Rounding.round(result / divisor, 6);
    }

    public boolean requiresIndividualEvaluation(MilestoneQuerySpec spec) {
        return spec.having() != null
                || spec.divisor() != null
                || isCountryScoped(spec)
                || (spec.select().offset() != null && spec.select().offset() != 0)
                || (spec.groupBy() != null && !spec.groupBy().isEmpty())
                || (spec.orderBy() != null && !spec.orderBy().isEmpty());
    }

    public Map<UUID, Double> evaluateBatch(List<Milestone> milestones, Long userId) {
        if (milestones.isEmpty()) {
            return Map.of();
        }
        Map<BatchKey, List<Milestone>> groups = new LinkedHashMap<>();
        for (Milestone milestone : milestones) {
            groups.computeIfAbsent(batchKey(milestone), key -> new ArrayList<>()).add(milestone);
        }
        Map<UUID, Double> results = new HashMap<>();
        for (List<Milestone> group : groups.values()) {
            results.putAll(evaluateGroup(group, userId));
        }
        return results;
    }

    public Score findQualifyingScore(MilestoneQuerySpec spec, Long userId, UUID categoryId,
            Double targetValue, String comparison) {
        if (!"scores".equals(spec.from())) {
            return null;
        }
        String function = spec.select().function().toUpperCase();
        MilestoneQuerySpec bounded = spec;
        if ("MAX".equals(function) || "MIN".equals(function)) {
            List<FilterSpec> filters = new ArrayList<>(
                    spec.filters() != null ? spec.filters() : List.<FilterSpec>of());
            filters.add(new FilterSpec(spec.select().column(), "LTE".equals(comparison) ? "<=" : ">=", targetValue));
            bounded = withFilters(spec, filters);
        }
        MilestoneQuerySpec identity = new MilestoneQuerySpec(
                new SelectSpec("PLAIN", "id"), bounded.from(), bounded.filters(), null, null, null, null,
                List.of(new OrderBySpec("time_set", "ASC")), 1, bounded.orGroups(), bounded.scope());

        Compiled compiled = compiler.scalar(identity, Context.of(userId, categoryId));
        Object id = bind(compiled).getResultList().stream().findFirst().orElse(null);
        return id == null ? null : entityManager.find(Score.class, toUuid(id));
    }

    public MilestoneSchemaResponse getSchema() {
        Map<String, List<ColumnInfo>> tables = new LinkedHashMap<>();
        for (MilestoneSource source : registry.all()) {
            List<ColumnInfo> columns = source.columns().values().stream()
                    .map(this::toColumnInfo)
                    .toList();
            tables.put(source.name(), columns);
        }
        return new MilestoneSchemaResponse(tables,
                AGGREGATE_FUNCTIONS.stream().sorted().toList(),
                Stream.concat(OPERATORS.stream(), NULL_OPERATORS.stream()).sorted().toList());
    }

    private ColumnInfo toColumnInfo(MilestoneSourceColumn column) {
        List<String> values = null;
        if (column.type() == MilestoneValueType.ENUM && column.enumClass() != null) {
            values = Arrays.stream(column.enumClass().getEnumConstants()).map(Enum::name).toList();
        }
        return new ColumnInfo(column.key(), column.type().name().toLowerCase(), values);
    }

    private Double evaluateSingle(MilestoneQuerySpec spec, Long userId, UUID categoryId) {
        return evaluateSingle(spec, userId, categoryId, havingValue(spec, userId, categoryId));
    }

    private Double evaluateSingle(MilestoneQuerySpec spec, Long userId, UUID categoryId, Double havingValue) {
        Context context = Context.of(userId, categoryId);
        if (spec.having() != null && spec.having().valueQuery() != null) {
            context = context.withHavingValue(havingValue);
        }
        return toDouble(bind(compiler.scalar(spec, context)).getSingleResult());
    }

    private Double havingValue(MilestoneQuerySpec spec, Long userId, UUID categoryId) {
        if (spec.having() == null || spec.having().valueQuery() == null) {
            return null;
        }
        return evaluateSingle(spec.having().valueQuery(), userId, categoryId);
    }

    private Map<UUID, Double> evaluateGroup(List<Milestone> group, Long userId) {
        Milestone reference = group.get(0);
        UUID categoryId = reference.getCategory() != null ? reference.getCategory().getId() : null;
        if (group.size() == 1) {
            return Collections.singletonMap(reference.getId(),
                    evaluate(reference.getQuerySpec(), userId, categoryId));
        }

        Map<SelectSpec, List<UUID>> byProjection = new LinkedHashMap<>();
        for (Milestone milestone : group) {
            SelectSpec select = milestone.getQuerySpec().select();
            byProjection.computeIfAbsent(new SelectSpec(select.function().toUpperCase(), select.column()),
                    key -> new ArrayList<>()).add(milestone.getId());
        }

        List<SelectSpec> projections = new ArrayList<>(byProjection.keySet());
        Compiled compiled = compiler.multi(projections, reference.getQuerySpec(), Context.of(userId, categoryId));
        Object row = bind(compiled).getSingleResult();

        Map<UUID, Double> results = new HashMap<>();
        for (int i = 0; i < projections.size(); i++) {
            Double value = toDouble(projections.size() == 1 ? row : ((Object[]) row)[i]);
            for (UUID milestoneId : byProjection.get(projections.get(i))) {
                results.put(milestoneId, value);
            }
        }
        return results;
    }

    private Query bind(Compiled compiled) {
        Query query = entityManager.createNativeQuery(compiled.sql());
        compiled.params().forEach(query::setParameter);
        return query;
    }

    private MilestoneQuerySpec withFilters(MilestoneQuerySpec spec, List<FilterSpec> filters) {
        return new MilestoneQuerySpec(spec.select(), spec.from(), filters, spec.having(), spec.divisor(),
                spec.groupBy(), spec.outerFunction(), spec.orderBy(), spec.limit(), spec.orGroups(), spec.scope());
    }

    private UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private boolean isCountryScoped(MilestoneQuerySpec spec) {
        return spec.scope() != null && "COUNTRY".equalsIgnoreCase(spec.scope());
    }

    private record BatchKey(String from, UUID categoryId, String signature) {
    }

    private BatchKey batchKey(Milestone milestone) {
        MilestoneQuerySpec spec = milestone.getQuerySpec();
        StringBuilder signature = new StringBuilder(filterSignature(spec.filters()));
        if (spec.orGroups() != null && !spec.orGroups().isEmpty()) {
            signature.append("|OR:").append(spec.orGroups().stream()
                    .map(this::filterSignature)
                    .collect(Collectors.joining("|")));
        }
        if (spec.scope() != null) {
            signature.append("|SCOPE:").append(spec.scope());
        }
        return new BatchKey(spec.from(),
                milestone.getCategory() != null ? milestone.getCategory().getId() : null,
                signature.toString());
    }

    private String filterSignature(List<FilterSpec> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        StringBuilder signature = new StringBuilder();
        for (FilterSpec filter : filters.stream().sorted(Comparator.comparing(FilterSpec::column)).toList()) {
            signature.append(filter.column()).append('|').append(filter.operator()).append('|');
            appendTransform(signature, "TF", filter.transform());
            appendTransform(signature, "CRTF", filter.columnRefTransform());
            if (filter.columnRef() != null) {
                signature.append("REF:").append(filter.columnRef()).append('|');
            }
            if (filter.subquery() != null) {
                signature.append("SUB(").append(filter.subquery().from()).append(',')
                        .append(filter.subquery().select().function()).append(',')
                        .append(filter.subquery().select().column()).append(',')
                        .append(filterSignature(filter.subquery().filters())).append(')');
            } else {
                signature.append(filter.value());
            }
            signature.append(';');
        }
        return signature.toString();
    }

    private void appendTransform(StringBuilder signature, String label, TransformSpec transform) {
        if (transform == null) {
            return;
        }
        signature.append(label).append(':').append(transform.function())
                .append(',').append(transform.argument()).append('|');
    }

    private void validateSpec(MilestoneQuerySpec spec, MilestoneSource parent) {
        if (spec == null || spec.select() == null || spec.from() == null) {
            throw new ValidationException("query_spec must include select and from fields");
        }
        MilestoneSource source = registry.get(spec.from());
        if (source == null) {
            throw new ValidationException("Unsupported table: " + spec.from() + ". Allowed: " + registry.names());
        }
        String function = spec.select().function().toUpperCase();
        if (!AGGREGATE_FUNCTIONS.contains(function)) {
            throw new ValidationException("Unsupported function: " + spec.select().function()
                    + ". Allowed: " + AGGREGATE_FUNCTIONS);
        }
        if ("PLAIN".equals(function) && parent == null) {
            throw new ValidationException("PLAIN function is only valid in subqueries");
        }
        requireColumn(source, spec.select().column(), "select column");

        for (FilterSpec filter : spec.filters() != null ? spec.filters() : List.<FilterSpec>of()) {
            validateFilter(filter, source, parent);
        }
        if (spec.orGroups() != null) {
            for (List<FilterSpec> group : spec.orGroups()) {
                if (group == null || group.isEmpty()) {
                    throw new ValidationException("or_groups entries must not be null or empty");
                }
                for (FilterSpec filter : group) {
                    validateFilter(filter, source, parent);
                }
            }
        }
        validateHaving(spec, source);
        if (spec.divisor() != null) {
            validateSpec(spec.divisor(), null);
        }
        validateGrouping(spec, source);
        validateOrdering(spec, source);
        validateScope(spec, source);
    }

    private void validateHaving(MilestoneQuerySpec spec, MilestoneSource source) {
        HavingSpec having = spec.having();
        if (having == null) {
            return;
        }
        if (!AGGREGATE_FUNCTIONS.contains(having.function().toUpperCase())) {
            throw new ValidationException("Unsupported having function: " + having.function());
        }
        requireColumn(source, having.column(), "having column");
        if (!OPERATORS.contains(having.operator())) {
            throw new ValidationException("Unsupported having operator: " + having.operator());
        }
        if (having.value() == null && having.valueQuery() == null) {
            throw new ValidationException("Having must have either value or value_query");
        }
        if (having.valueQuery() != null) {
            validateSpec(having.valueQuery(), null);
        }
    }

    private void validateGrouping(MilestoneQuerySpec spec, MilestoneSource source) {
        if (spec.groupBy() == null || spec.groupBy().isEmpty()) {
            return;
        }
        for (GroupBySpec groupBy : spec.groupBy()) {
            requireColumn(source, groupBy.column(), "group_by column");
            if (groupBy.cast() != null && !"DATE".equalsIgnoreCase(groupBy.cast())) {
                throw new ValidationException("Unsupported group_by cast: " + groupBy.cast() + ". Allowed: DATE");
            }
        }
        if (spec.outerFunction() == null) {
            throw new ValidationException("outer_function is required when group_by is present");
        }
        if (!AGGREGATE_FUNCTIONS.contains(spec.outerFunction().toUpperCase())) {
            throw new ValidationException("Unsupported outer_function: " + spec.outerFunction());
        }
    }

    private void validateOrdering(MilestoneQuerySpec spec, MilestoneSource source) {
        if (spec.orderBy() == null) {
            return;
        }
        for (OrderBySpec order : spec.orderBy()) {
            requireColumn(source, order.column(), "order_by column");
            if (order.direction() != null && !"ASC".equalsIgnoreCase(order.direction())
                    && !"DESC".equalsIgnoreCase(order.direction())) {
                throw new ValidationException("Unsupported order_by direction: " + order.direction());
            }
        }
        if (spec.limit() == null) {
            throw new ValidationException("limit is required when order_by is present");
        }
    }

    private void validateScope(MilestoneQuerySpec spec, MilestoneSource source) {
        if (spec.scope() == null) {
            return;
        }
        if (!SCOPES.contains(spec.scope().toUpperCase())) {
            throw new ValidationException("Unsupported scope: " + spec.scope() + ". Allowed: " + SCOPES);
        }
        if (isCountryScoped(spec) && source.countryTemplate() == null) {
            throw new ValidationException("COUNTRY scope is not supported for table '" + spec.from() + "'");
        }
    }

    private void validateFilter(FilterSpec filter, MilestoneSource source, MilestoneSource parent) {
        requireColumn(source, filter.column(), "filter column");
        boolean subqueryOperator = SUBQUERY_OPERATORS.contains(filter.operator());
        boolean nullOperator = NULL_OPERATORS.contains(filter.operator());
        if (!OPERATORS.contains(filter.operator()) && !subqueryOperator && !nullOperator) {
            throw new ValidationException("Unsupported operator: " + filter.operator()
                    + ". Allowed: " + OPERATORS + ", null checks: " + NULL_OPERATORS
                    + " or subquery operators: " + SUBQUERY_OPERATORS);
        }
        if (nullOperator) {
            if (filter.value() != null || filter.subquery() != null || filter.columnRef() != null) {
                throw new ValidationException(
                        "Operator '" + filter.operator() + "' takes no value on column: " + filter.column());
            }
            validateTransform(filter.transform());
            return;
        }
        if (filter.subquery() != null) {
            validateSpec(filter.subquery(), source);
        } else if (subqueryOperator) {
            throw new ValidationException(
                    "Operator '" + filter.operator() + "' requires a subquery on column: " + filter.column());
        } else if (filter.columnRef() != null) {
            String reference = filter.columnRef();
            if (reference.startsWith("OUTER.")) {
                requireColumn(parent != null ? parent : source, reference.substring(6), "column_ref");
            } else {
                requireColumn(source, reference, "column_ref");
            }
        } else if (filter.value() == null) {
            throw new ValidationException("Filter value must not be null for column: " + filter.column());
        }
        validateTransform(filter.transform());
        validateTransform(filter.columnRefTransform());
    }

    private void validateTransform(TransformSpec transform) {
        if (transform == null) {
            return;
        }
        if (!TRANSFORM_FUNCTIONS.contains(transform.function())) {
            throw new ValidationException("Unsupported transform function: " + transform.function()
                    + ". Allowed: " + TRANSFORM_FUNCTIONS);
        }
        if (transform.argument() == null) {
            throw new ValidationException("Transform argument must not be null");
        }
        if ("MOD".equals(transform.function())) {
            compiler.modArgument(String.valueOf(transform.argument()).trim());
        }
    }

    private void requireColumn(MilestoneSource source, String column, String what) {
        if (column == null || !source.hasColumn(column)) {
            throw new ValidationException("Unsupported " + what + " '" + column
                    + "' for table '" + source.name() + "'");
        }
    }
}
