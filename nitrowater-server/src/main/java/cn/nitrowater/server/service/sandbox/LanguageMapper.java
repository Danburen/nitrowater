package cn.nitrowater.server.service.sandbox;

import java.util.Map;
import java.util.Optional;

/**
 * Maps the front-end language ids to Piston runtime names and source file names.
 *
 * <p>Piston picks the run entry point from the file name for compiled languages, so the mapping
 * must be exact (e.g. Java needs a {@code Main.java} holding a {@code Main} class). Scripting
 * runtimes (Python / JavaScript / TypeScript / Lua) are resolved by their own runtime names.</p>
 *
 * <p>The Piston {@code node} package registers the runtime as {@code javascript} (see
 * {@code bin/install-piston-langs.ps1}), so the front-end id {@code javascript} maps to it.</p>
 */
public final class LanguageMapper {

    private static final Map<String, Target> TARGETS = Map.ofEntries(
            Map.entry("java", new Target("java", "*", "Main.java")),
            Map.entry("c", new Target("c", "*", "main.c")),
            Map.entry("cpp", new Target("c++", "*", "main.cpp")),
            Map.entry("python", new Target("python", "*", "main.py")),
            Map.entry("javascript", new Target("javascript", "*", "main.js")),
            Map.entry("typescript", new Target("typescript", "*", "main.ts")),
            Map.entry("lua", new Target("lua", "*", "main.lua")));

    private LanguageMapper() {
    }

    /** Resolves a front-end language id, or empty when the language is not served by the sandbox. */
    public static Optional<Target> resolve(String languageId) {
        return languageId == null ? Optional.empty() : Optional.ofNullable(TARGETS.get(languageId));
    }

    /**
     * A resolved Piston execution target.
     *
     * @param pistonLanguage Piston runtime name
     * @param version SemVer selector ({@code *} = newest installed)
     * @param fileName source file name to write
     */
    public record Target(String pistonLanguage, String version, String fileName) {
    }
}
