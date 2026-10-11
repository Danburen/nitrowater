package cn.nitrowater.server.sandbox;

import java.util.Map;
import java.util.Optional;

/**
 * Maps the front-end language ids to Piston runtime names and source file names.
 *
 * <p>Piston picks the run entry point from the file name for compiled languages, so the mapping
 * must be exact (e.g. Java needs a {@code Main.java} holding a {@code Main} class).</p>
 */
final class LanguageMapper {

    private static final Map<String, Target> TARGETS = Map.of(
            "java", new Target("java", "*", "Main.java"),
            "c", new Target("c", "*", "main.c"),
            "cpp", new Target("c++", "*", "main.cpp"));

    private LanguageMapper() {
    }

    /** Resolves a front-end language id, or empty when the language is not served by the sandbox. */
    static Optional<Target> resolve(String languageId) {
        return languageId == null ? Optional.empty() : Optional.ofNullable(TARGETS.get(languageId));
    }

    /**
     * A resolved Piston execution target.
     *
     * @param pistonLanguage Piston runtime name
     * @param version SemVer selector ({@code *} = newest installed)
     * @param fileName source file name to write
     */
    record Target(String pistonLanguage, String version, String fileName) {
    }
}
