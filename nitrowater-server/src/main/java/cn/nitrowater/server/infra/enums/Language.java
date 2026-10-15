package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/**
 * Programming language of a submission.
 *
 * <p>Codes are <b>append-only</b>: never renumber an existing code (it is persisted).</p>
 */
@Getter
public enum Language {
    JAVA(0, "java"),
    C(1, "c"),
    CPP(2, "c++"),
    PYTHON(3, "python"),
    JAVASCRIPT(4, "javascript"),
    TYPESCRIPT(5, "typescript"),
    LUA(6, "lua");

    private final short value;
    /** Piston runtime name (see the sandbox LanguageMapper). */
    private final String pistonName;

    Language(int value, String pistonName) {
        this.value = (short) value;
        this.pistonName = pistonName;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching language
     * @throws IllegalArgumentException when the value is unknown
     */
    public static Language fromValue(int value) {
        for (Language language : values()) {
            if (language.value == value) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unknown Language value: " + value);
    }

    /**
     * Resolves a front-end language id (e.g. {@code cpp}, {@code javascript}) — the ids equal the
     * lower-cased enum names.
     *
     * @param id front-end language id
     * @return the matching language
     * @throws IllegalArgumentException when the id is unknown
     */
    public static Language fromId(String id) {
        if (id != null) {
            String normalized = id.trim().toLowerCase();
            for (Language language : values()) {
                if (language.name().toLowerCase().equals(normalized)) {
                    return language;
                }
            }
        }
        throw new IllegalArgumentException("Unknown language id: " + id);
    }

    /**
     * Source file name handed to Piston. Compiled runtimes get a <b>bare</b> base name because
     * Piston appends the runtime's own extension (passing {@code Main.java} yields
     * {@code Main.java.java}); scripting runtimes keep their extension verbatim.
     *
     * @return the file name to write the source as
     */
    public String fileName() {
        return switch (this) {
            case JAVA -> "Main";
            case C, CPP, TYPESCRIPT -> "main";
            case PYTHON -> "main.py";
            case JAVASCRIPT -> "main.js";
            case LUA -> "main.lua";
        };
    }
}
