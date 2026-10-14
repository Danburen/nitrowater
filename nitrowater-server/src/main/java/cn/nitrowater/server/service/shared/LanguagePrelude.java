package cn.nitrowater.server.service.shared;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepends a language-specific prelude of common imports/includes to user code before it reaches
 * the sandbox, so submissions may omit boilerplate (mirroring how online judges auto-import common
 * libraries such as {@code java.util.*}).
 *
 * <p>Only languages with a well-known, safe prelude are touched; everything else passes through
 * unchanged. Because a header shifts the body down, compiler diagnostics are kept faithful to the
 * user's own line numbers: C/C++ emit an explicit {@code #line} directive, while Java/Python
 * references are rewound by {@link #deoffset} using the header's line count.</p>
 */
public final class LanguagePrelude {

    /** A Java {@code package ...;} declaration, which must precede imports and so is blanked out. */
    private static final Pattern JAVA_PACKAGE =
            Pattern.compile("(?m)^[ \\t]*package[ \\t]+[\\w.]+[ \\t]*;[ \\t]*");

    /** {@code Main.java:12:} references inside javac diagnostics. */
    private static final Pattern JAVA_REF = Pattern.compile("\\b([\\w$]+\\.java):(\\d+):");

    /** {@code File "...", line 12} references inside CPython tracebacks. */
    private static final Pattern PY_REF = Pattern.compile("File \"[^\"]*\", line (\\d+)");

    private static final String JAVA = "java";
    private static final String C = "c";
    private static final String CPP = "c++";
    private static final String PYTHON = "python";

    private LanguagePrelude() {
    }

    /**
     * @param pistonLanguage Piston runtime name (e.g. {@code java}, {@code c++})
     * @return the prelude header ending in a newline, or {@code ""} when the language has none
     */
    public static String header(String pistonLanguage) {
        return switch (pistonLanguage) {
            case JAVA -> "import java.util.*;\n"
                    + "import java.util.stream.*;\n"
                    + "import java.util.function.*;\n"
                    + "import java.math.*;\n"
                    + "import java.io.*;\n";
            case C -> "#include <stdio.h>\n"
                    + "#include <stdlib.h>\n"
                    + "#include <string.h>\n"
                    + "#include <math.h>\n"
                    + "#include <stdbool.h>\n"
                    + "#include <ctype.h>\n"
                    + "#include <limits.h>\n";
            case CPP -> "#include <bits/stdc++.h>\n"
                    + "using namespace std;\n";
            // Both module imports and star-imports, so users may write either `math.sqrt`/`sys.stdin`
            // or the unqualified `sqrt`/`Counter`/`reduce` — matching how LeetCode exposes them.
            case PYTHON -> "import sys\n"
                    + "import re\n"
                    + "import math\n"
                    + "import collections\n"
                    + "import heapq\n"
                    + "import bisect\n"
                    + "import itertools\n"
                    + "import functools\n"
                    + "from typing import *\n"
                    + "from collections import *\n"
                    + "from heapq import *\n"
                    + "from bisect import *\n"
                    + "from itertools import *\n"
                    + "from functools import *\n"
                    + "from math import *\n";
            default -> "";
        };
    }

    /**
     * Injects the prelude ahead of {@code code}.
     *
     * @param pistonLanguage Piston runtime name
     * @param code the user's source code
     * @return the code to hand to the sandbox (a no-op when the language has no prelude)
     */
    public static String wrap(String pistonLanguage, String code) {
        String body = code == null ? "" : code;
        String header = header(pistonLanguage);
        if (header.isEmpty()) {
            return body;
        }
        if (JAVA.equals(pistonLanguage)) {
            // A package declaration must stay first; blank the line so line numbers are preserved.
            body = JAVA_PACKAGE.matcher(body).replaceFirst("");
        }
        if (C.equals(pistonLanguage) || CPP.equals(pistonLanguage)) {
            // gcc honours #line, so diagnostics keep the user's own line numbers.
            return header + "#line 1\n" + body;
        }
        return header + body;
    }

    /**
     * @param pistonLanguage Piston runtime name
     * @return how many lines {@link #wrap} prepends to the user's code (zero when the prelude is
     *         absent or uses {@code #line}, which keeps diagnostics offset-free)
     */
    public static int offsetLines(String pistonLanguage) {
        if (JAVA.equals(pistonLanguage) || PYTHON.equals(pistonLanguage)) {
            return countLines(header(pistonLanguage));
        }
        return 0;
    }

    /**
     * Rewinds line numbers in compiler diagnostics by the prelude offset, so errors point at the
     * user's own line numbers.
     *
     * @param pistonLanguage Piston runtime name
     * @param text compiler diagnostics (may be {@code null})
     * @return the adjusted diagnostics, or the original text when nothing needs adjusting
     */
    public static String deoffset(String pistonLanguage, String text) {
        return deoffsetBy(pistonLanguage, text, offsetLines(pistonLanguage));
    }

    /**
     * Rewinds line numbers in compiler diagnostics by an explicit offset, so errors point at the
     * user's own line numbers. Used by the LeetCode-style harness, whose prefix length is not the
     * plain prelude header.
     *
     * @param pistonLanguage Piston runtime name
     * @param text compiler diagnostics (may be {@code null})
     * @param offset number of lines to subtract from each referenced line number
     * @return the adjusted diagnostics, or the original text when nothing needs adjusting
     */
    public static String deoffsetBy(String pistonLanguage, String text, int offset) {
        if (text == null || text.isEmpty() || offset <= 0) {
            return text;
        }
        if (JAVA.equals(pistonLanguage)) {
            return JAVA_REF.matcher(text).replaceAll(m -> Matcher.quoteReplacement(
                    m.group(1) + ":" + Math.max(1, Integer.parseInt(m.group(2)) - offset) + ":"));
        }
        if (PYTHON.equals(pistonLanguage)) {
            return PY_REF.matcher(text).replaceAll(m -> Matcher.quoteReplacement(
                    "File \"main.py\", line " + Math.max(1, Integer.parseInt(m.group(1)) - offset)));
        }
        return text;
    }

    private static int countLines(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        if (text.charAt(text.length() - 1) != '\n') {
            lines++;
        }
        return lines;
    }
}
