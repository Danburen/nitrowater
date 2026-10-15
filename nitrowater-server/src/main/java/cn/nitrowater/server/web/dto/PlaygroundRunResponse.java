package cn.nitrowater.server.web.dto;

import java.util.List;

/**
 * Result of a playground self-test run.
 *
 * @param status overall run status: {@code OK}/{@code CE}/{@code TO}/{@code OL}/{@code RE}/{@code SE}
 * @param compileMessage compiler diagnostics when the status is {@code CE}, else {@code null}
 * @param cases one entry per sample, in the order they were supplied
 */
public record PlaygroundRunResponse(String status, String compileMessage, List<PlaygroundCaseResult> cases) {
}
