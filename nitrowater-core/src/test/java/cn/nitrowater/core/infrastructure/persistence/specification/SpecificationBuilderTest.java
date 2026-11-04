package cn.nitrowater.core.infrastructure.persistence.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Locks the two behaviours every spec depends on: keyword matching is escaped (a user typing
 * {@code %} or {@code _} cannot widen the search) and null / blank filters never reach the query.
 */
@SuppressWarnings("unchecked")
class SpecificationBuilderTest {

    private final Root<Object> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

    /** A literal percent / underscore / backslash must survive as text, not as LIKE syntax. */
    @Test
    void escapesLikeWildcards() {
        Path<Object> title = mock(Path.class);
        Expression<String> asString = mock(Expression.class);
        Expression<String> lower = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get("title")).thenReturn(title);
        when(title.as(String.class)).thenReturn(asString);
        when(cb.lower(asString)).thenReturn(lower);
        when(cb.like(eq(lower), anyString(), eq('\\'))).thenReturn(predicate);

        Specification<Object> spec = SpecificationBuilder.builder()
                .likeIgnoreCase("title", " 100%_a\\b ")
                .build();

        assertThat(spec.toPredicate(root, query, cb)).isSameAs(predicate);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(cb).like(eq(lower), pattern.capture(), eq('\\'));
        assertThat(pattern.getValue()).isEqualTo("%100\\%\\_a\\\\b%");
    }

    /** An absent filter means "do not constrain", not "match nothing". */
    @Test
    void ignoresNullBlankAndMissingFilters() {
        Predicate all = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(all);

        Specification<Object> spec = SpecificationBuilder.builder()
                .eq("status", null)
                .notEqual("uid", null)
                .likeIgnoreCase("title", "   ")
                .likeIgnoreCaseAny(null, "name", "code")
                .in("kind", null)
                .greaterThanOrEqualTo("createdAt", null)
                .lessThan("createdAt", null)
                .build();

        assertThat(spec.toPredicate(root, query, cb)).isSameAs(all);
        verifyNoInteractions(root);
    }

    /** An explicit empty allow-list can never match a row, so the page is empty by construction. */
    @Test
    void emptyInListMatchesNothing() {
        Predicate none = mock(Predicate.class);
        when(cb.disjunction()).thenReturn(none);

        Specification<Object> spec = SpecificationBuilder.builder()
                .in("kind", List.of())
                .build();

        assertThat(spec.toPredicate(root, query, cb)).isSameAs(none);
        verifyNoInteractions(root);
    }
}
