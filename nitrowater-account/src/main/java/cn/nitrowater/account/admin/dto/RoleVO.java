package cn.nitrowater.account.admin.dto;

import java.time.Instant;

/**
 * Role view for the admin console.
 *
 * @param id          role id
 * @param code        unique role code (e.g. {@code ADMIN})
 * @param name        display name
 * @param description optional description
 * @param builtin     true for built-in roles that cannot be deleted / re-coded
 * @param userCount   number of accounts currently bound to this role
 * @param createdAt   creation timestamp
 */
public record RoleVO(
        Long id,
        String code,
        String name,
        String description,
        Boolean builtin,
        long userCount,
        Instant createdAt) {
}
