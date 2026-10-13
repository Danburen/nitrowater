package cn.nitrowater.account.admin.dto;

import java.util.List;

/**
 * Current admin identity for the SPA bootstrap (who am I + which authorities do I hold).
 *
 * @param uid      account id
 * @param username login name
 * @param name     display name
 * @param roles    effective authorities (ROLE_-prefixed, SUPER_ADMIN implies ROLE_ADMIN)
 */
public record AdminSessionVO(
        long uid,
        String username,
        String name,
        List<String> roles) {
}
