package cn.nitrowater.server.service.judge.harness;

import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.service.judge.JudgeSignature;

/**
 * Builds the single-file LeetCode-style harness for one language: a program that drives the user's
 * {@code Solution} over a JSON array of cases on stdin and prints one JSON result per case.
 *
 * <p>Only harness <b>generation</b> is language-specific — running the program once, recognising a
 * compile error, mapping time/memory/runtime failures, comparing each case's JSON line and
 * aggregating the verdict are all language-agnostic and live once in {@link FunctionRunner}.
 * Implementations are Spring beans discovered as a {@code List<FunctionHarness>}, so adding a
 * language means adding one component rather than editing a central switch.</p>
 */
public interface FunctionHarness {

    /** @return the language this harness generates code for */
    Language language();

    /**
     * @param signature the problem's parsed method signature
     * @param userCode the user's submitted {@code Solution} source
     * @return the generated program plus the line offset of the user's own code
     * @throws IllegalArgumentException when the signature uses a type the harness cannot bind
     */
    GeneratedHarness generate(JudgeSignature signature, String userCode);
}
