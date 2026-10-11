package cn.nitrowater.server.service.shared;

import cn.nitrowater.server.infra.third.PistonClient;

/**
 * A language-agnostic execution request for one run of user code.
 *
 * <p>This is the sandbox package's own input shape; it deliberately does not mirror Piston's
 * wire format so the Piston API stays confined to {@link PistonClient}.</p>
 *
 * @param pistonLanguage Piston runtime name (e.g. {@code java}, {@code c}, {@code c++})
 * @param version a SemVer selector for the runtime; {@code *} selects the newest installed
 * @param fileName the source file name Piston writes (name matters for compiled languages)
 * @param code the user's submitted source code
 * @param stdin text piped to the program's standard input (may be empty)
 * @param compileTimeoutMs compile-stage timeout in milliseconds
 * @param runTimeoutMs run-stage timeout in milliseconds
 * @param compileMemoryBytes compile-stage memory cap in bytes
 * @param runMemoryBytes run-stage memory cap in bytes
 */
public record ExecSpec(
        String pistonLanguage,
        String version,
        String fileName,
        String code,
        String stdin,
        int compileTimeoutMs,
        int runTimeoutMs,
        long compileMemoryBytes,
        long runMemoryBytes) {
}
