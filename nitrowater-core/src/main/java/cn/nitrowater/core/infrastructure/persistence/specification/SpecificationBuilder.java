package cn.nitrowater.core.infrastructure.persistence.specification;

import cn.nitrowater.lib.utils.StringUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Small fluent builder for the optional filters used by paged list queries.
 *
 * <p>Every method is a no-op when its value is {@code null}/blank, so callers can pass request
 * parameters straight through instead of writing a chain of {@code if} blocks and Criteria
 * predicates. Dotted attributes ({@code id.uid}, {@code id.usageDate}) walk the path, and
 * {@link #add(PredicateFactory)} is the escape hatch for joins/subqueries/arithmetic.</p>
 *
 * <p>Keyword matching is always case-insensitive and escapes SQL wildcards, so a user typing
 * {@code %} or {@code _} cannot widen the search. The builder never concatenates SQL text: it
 * only supplies bind values to Criteria predicates.</p>
 *
 * @param <T> entity type
 */
public final class SpecificationBuilder<T> {

    /** Escape character used by {@link #likeIgnoreCase(String, String)}. */
    private static final char LIKE_ESCAPE = '\\';

    /** A Criteria predicate that also needs the query (subquery/order-by use). */
    @FunctionalInterface
    public interface PredicateFactory<T> {
        Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb);
    }

    private final List<Specification<T>> parts = new ArrayList<>();

    private SpecificationBuilder() {
    }

    /** @param <T> entity type @return an empty builder */
    public static <T> SpecificationBuilder<T> builder() {
        return new SpecificationBuilder<>();
    }

    /** Adds a pre-built specification; {@code null} is ignored. */
    public SpecificationBuilder<T> add(Specification<T> specification) {
        if (specification != null) {
            parts.add(specification);
        }
        return this;
    }

    /** Adds a custom predicate factory (join, subquery, arithmetic, ...). */
    public SpecificationBuilder<T> where(PredicateFactory<T> predicate) {
        if (predicate != null) {
            parts.add(predicate::toPredicate);
        }
        return this;
    }

    /** Adds the predicate only when {@code condition} is true. */
    public SpecificationBuilder<T> when(boolean condition, PredicateFactory<T> predicate) {
        return condition ? where(predicate) : this;
    }

    /** {@code attribute = value}; ignored when {@code value} is {@code null}. */
    public SpecificationBuilder<T> eq(String attribute, Object value) {
        return value == null ? this : add((root, query, cb) -> cb.equal(path(root, attribute), value));
    }

    /** {@code attribute <> value}; ignored when {@code value} is {@code null}. */
    public SpecificationBuilder<T> notEqual(String attribute, Object value) {
        return value == null ? this : add((root, query, cb) -> cb.notEqual(path(root, attribute), value));
    }

    /** Case-insensitive {@code contains}; blank values are ignored. */
    public SpecificationBuilder<T> likeIgnoreCase(String attribute, String value) {
        if (StringUtil.isBlank(value)) {
            return this;
        }
        String pattern = containsPattern(value);
        return add((root, query, cb) -> cb.like(cb.lower(path(root, attribute).as(String.class)),
                pattern, LIKE_ESCAPE));
    }

    /** ORs a case-insensitive {@code contains} across several attributes; blank values are ignored. */
    public SpecificationBuilder<T> likeIgnoreCaseAny(String value, String... attributes) {
        if (StringUtil.isBlank(value) || attributes == null || attributes.length == 0) {
            return this;
        }
        String pattern = containsPattern(value);
        return add((root, query, cb) -> {
            Predicate[] predicates = java.util.Arrays.stream(attributes)
                    .map(attribute -> cb.like(cb.lower(path(root, attribute).as(String.class)),
                            pattern, LIKE_ESCAPE))
                    .toArray(Predicate[]::new);
            return cb.or(predicates);
        });
    }

    /** {@code attribute IN (values)}; ignored for a {@code null} collection, always false when empty. */
    public SpecificationBuilder<T> in(String attribute, Collection<?> values) {
        if (values == null) {
            return this;
        }
        if (values.isEmpty()) {
            return where((root, query, cb) -> cb.disjunction());
        }
        return where((root, query, cb) -> path(root, attribute).in(values));
    }

    /** {@code attribute >= value}; ignored when {@code value} is {@code null}. */
    public SpecificationBuilder<T> greaterThanOrEqualTo(String attribute, Comparable<?> value) {
        return compare(attribute, value, CompareOp.GE);
    }

    /** {@code attribute < value}; ignored when {@code value} is {@code null}. */
    public SpecificationBuilder<T> lessThan(String attribute, Comparable<?> value) {
        return compare(attribute, value, CompareOp.LT);
    }

    /** {@code attribute > value}; ignored when {@code value} is {@code null}. */
    public SpecificationBuilder<T> greaterThan(String attribute, Comparable<?> value) {
        return compare(attribute, value, CompareOp.GT);
    }

    /** {@code attribute <= value}; ignored when {@code value} is {@code null}. */
    public SpecificationBuilder<T> lessThanOrEqualTo(String attribute, Comparable<?> value) {
        return compare(attribute, value, CompareOp.LE);
    }

    /** @return the combined spec; an empty builder matches every row */
    public Specification<T> build() {
        if (parts.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return parts.stream()
                .reduce((left, right) -> left.and(right))
                .orElse((root, query, cb) -> cb.conjunction());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private SpecificationBuilder<T> compare(String attribute, Comparable<?> value, CompareOp operation) {
        if (value == null) {
            return this;
        }
        return where((root, query, cb) -> {
            Expression expression = path(root, attribute);
            return switch (operation) {
                case GE -> cb.greaterThanOrEqualTo(expression, (Comparable) value);
                case GT -> cb.greaterThan(expression, (Comparable) value);
                case LE -> cb.lessThanOrEqualTo(expression, (Comparable) value);
                case LT -> cb.lessThan(expression, (Comparable) value);
            };
        });
    }

    private enum CompareOp {
        GE, GT, LE, LT
    }

    /** Walks a dotted attribute path ({@code id.uid} → {@code root.get("id").get("uid")}). */
    @SuppressWarnings("unchecked")
    private static <T> Path<Object> path(Root<T> root, String attribute) {
        Path<?> current = root;
        for (String segment : attribute.split("\\.")) {
            current = current.get(segment);
        }
        return (Path<Object>) current;
    }

    /** @return {@code %escaped-lowercase-value%}, escaping {@code %}, {@code _} and the escape char */
    private static String containsPattern(String value) {
        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
