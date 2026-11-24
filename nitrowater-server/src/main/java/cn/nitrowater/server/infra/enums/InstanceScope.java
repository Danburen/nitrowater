package cn.nitrowater.server.infra.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permission scopes of an instance api-key.
 *
 * <p>Unlike the status flags, scopes are stored as a comma-separated code string
 * ({@code instance_credential.scopes}) rather than a numeric column — a key can hold several, and
 * the code is what V reads. This enum is the single vocabulary: it maps each code to the Spring
 * Security authority the api-key filter grants and parses the stored string without scattering
 * string literals across the code. Codes are append-only.</p>
 */
@Getter
public enum InstanceScope {
    TELEMETRY_WRITE("telemetry:write"),
    RPC_EXECUTE("rpc:execute");

    private final String code;

    InstanceScope(String code) {
        this.code = code;
    }

    /**
     * @return the Spring Security authority granted for this scope (e.g. {@code SCOPE_TELEMETRY_WRITE})
     */
    public String authority() {
        return "SCOPE_" + name();
    }

    /**
     * Parses a stored comma-separated scope string, ignoring blanks and unknown codes.
     *
     * @param csv persisted scopes (may be {@code null})
     * @return the recognised scopes, never {@code null}
     */
    public static Set<InstanceScope> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return new LinkedHashSet<>();
        }
        Set<InstanceScope> result = new LinkedHashSet<>();
        for (String token : csv.split(",")) {
            String code = token.trim().toLowerCase(Locale.ROOT);
            for (InstanceScope scope : values()) {
                if (scope.code.equals(code)) {
                    result.add(scope);
                }
            }
        }
        return result;
    }

    /**
     * Serialises scopes to the canonical comma-separated form stored in the database.
     *
     * @param scopes scopes to store
     * @return canonical CSV string
     */
    public static String serialize(Set<InstanceScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "";
        }
        return Arrays.stream(values())
                .filter(scopes::contains)
                .map(InstanceScope::getCode)
                .collect(Collectors.joining(","));
    }
}
