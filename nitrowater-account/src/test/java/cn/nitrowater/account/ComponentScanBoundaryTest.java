package cn.nitrowater.account;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the component-scan boundary of this application.
 *
 * <p>The app used to scan {@code cn.nitrowater} wholesale, which pulled
 * {@code cn.nitrowater.core.web.GlobalExceptionHandler} (a {@code @RestControllerAdvice} from
 * {@code nitrowater-core-web}) into the context alongside this service's own handler. Both are
 * annotated {@code @RestControllerAdvice}, so both derive the bean name {@code globalExceptionHandler}
 * and the context fails with {@code ConflictingBeanDefinitionException}.</p>
 *
 * <p>The application now scans only {@code cn.nitrowater.account} + {@code cn.nitrowater.core} and
 * excludes {@code cn.nitrowater.core.web}, where {@code ApiResponseAdvice} is pulled in explicitly
 * with {@code @Import} instead. This test fails if that exclusion pattern ever stops matching.</p>
 */
class ComponentScanBoundaryTest {

    /** Same pattern as {@code NitrowaterAccountApplication} — keep the two in sync. */
    private static final Pattern SHARED_WEB_PLUMBING = Pattern.compile("cn\\.nitrowater\\.core\\.web\\..*");

    @Test
    void sharedWebPlumbingIsNotComponentScanned() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        scanner.addExcludeFilter(new RegexPatternTypeFilter(SHARED_WEB_PLUMBING));

        Set<String> candidates = scanner.findCandidateComponents("cn.nitrowater.core").stream()
                .map(definition -> definition.getBeanClassName())
                .collect(Collectors.toSet());

        assertThat(candidates)
                .doesNotContain("cn.nitrowater.core.web.GlobalExceptionHandler")
                .doesNotContain("cn.nitrowater.core.web.ApiResponseAdvice")
                .contains("cn.nitrowater.core.application.account.impl.AccountCoreServiceImpl");
    }
}
