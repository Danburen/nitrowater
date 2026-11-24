package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Result of a dispatched remote command, stored as metadata in {@code instance_command_audit}. */
@Getter
public enum InstanceCommandResult {
    OK(0),
    ERROR(1),
    TIMEOUT(2);

    private final short value;

    InstanceCommandResult(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching result
     * @throws IllegalArgumentException when the value is unknown
     */
    public static InstanceCommandResult fromValue(int value) {
        for (InstanceCommandResult result : values()) {
            if (result.value == value) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unknown InstanceCommandResult value: " + value);
    }
}
