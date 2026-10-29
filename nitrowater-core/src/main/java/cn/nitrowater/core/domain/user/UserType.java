package cn.nitrowater.core.domain.user;

import lombok.Getter;

@Getter
public enum UserType {
    COMMON(0),
    ADMIN(1),
    BOT(2),
    MODERATOR(3),
    VIP(4),
    /** System account: no phone, password login only, never exposed to code login. */
    SYSTEM(5),
    /** Test account: optionally no phone, password login only. */
    TEST(6),;

    private final byte value;
    UserType(final int value) {
        this.value = (byte) value;
    }

    public static UserType fromValue(Byte value) {
        for (UserType userType : UserType.values()) {
            if (userType.value == value) {
                return userType;
            }
        }
        throw new IllegalArgumentException("Can't find User Type of" +value);
    }

    /**
     * Whether this account type may sign in with a one-time code (SMS / email).
     *
     * <p>System and test accounts are provisioned by operators and must never be reachable
     * through a target that a user could control, so they are password-only.</p>
     *
     * @return {@code false} for {@link #SYSTEM} and {@link #TEST}, {@code true} otherwise
     */
    public boolean isCodeLoginAllowed() {
        return this != SYSTEM && this != TEST;
    }
}
