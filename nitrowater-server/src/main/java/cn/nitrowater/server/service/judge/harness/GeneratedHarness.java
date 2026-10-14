package cn.nitrowater.server.service.judge.harness;

/**
 * A generated single-file harness program that drives a LeetCode-style {@code Solution} over stdin,
 * together with the line offset at which the user's own code begins.
 *
 * <p>The offset lets compiler diagnostics be rewound to the user's own line numbers, because every
 * harness prepends some fixed prefix (prelude and/or a driver class) ahead of the user's code.</p>
 *
 * @param source the complete program handed to the sandbox
 * @param userCodeLineOffset number of harness prefix lines before the user's first line
 */
public record GeneratedHarness(String source, int userCodeLineOffset) {
}
