package cn.nitrowater.server.service.sandbox.model;

/**
 * A one-off code run, detached from the HTTP request DTO.
 *
 * @param language front-end language id ({@code java}, {@code c}, {@code cpp}, {@code python},
 *                 {@code javascript}, {@code typescript} or {@code lua})
 * @param code the source code to execute
 * @param stdin optional standard input (may be {@code null}/empty)
 */
public record RunCommand(String language, String code, String stdin) {
}
