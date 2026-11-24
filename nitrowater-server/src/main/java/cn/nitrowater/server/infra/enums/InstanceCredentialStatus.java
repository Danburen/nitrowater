package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/**
 * Lifecycle of a VeloChatX instance api-key. Persisted as {@code tinyint UNSIGNED}.
 *
 * <p>{@code ROTATED} is a grace state kept after a key rotation: the old key may still report
 * telemetry but is refused for command execution. Codes are append-only.</p>
 */
@Getter
public enum InstanceCredentialStatus {
    ACTIVE(0),
    ROTATED(1),
    REVOKED(2);

    private final short value;

    InstanceCredentialStatus(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching status
     * @throws IllegalArgumentException when the value is unknown
     */
    public static InstanceCredentialStatus fromValue(int value) {
        for (InstanceCredentialStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown InstanceCredentialStatus value: " + value);
    }
}
