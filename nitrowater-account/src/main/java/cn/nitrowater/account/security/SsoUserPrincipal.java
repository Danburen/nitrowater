package cn.nitrowater.account.security;

import cn.nitrowater.core.domain.user.AccountStatus;
import cn.nitrowater.core.domain.user.User;
import cn.nitrowater.core.domain.user.UserType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Phase 2.4: SSO login principal.
 *
 * <p>Carries the identity context used by token customization
 * ({@code preferred_username / name / roles / did}).</p>
 *
 * <p>Stored by Spring Authorization Server's JDBC store (default typing), so it must be
 * Jackson-constructible: the creator + JsonIgnore'd computed getters keep the persisted
 * JSON to the 9 fields below.</p>
 *
 * <p>{@link #getUsername()} returns uid on purpose: Spring Authorization Server uses
 * {@code principal.getName()} as the token {@code sub}, keeping {@code sub = uid}
 * aligned with the Phase 1 JWT and the WaterFun gateway {@code X-User-Uid}.</p>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SsoUserPrincipal implements UserDetails {

    /** Account uid (token sub). */
    private final long uid;

    /** Login name, mapped to the preferred_username claim. */
    private final String loginName;

    private final String nickname;

    /** Canonical avatar resource uuid (mapped to the OIDC {@code picture} claim by the token customizer). */
    private final String avatarUuid;

    private final UserType userType;

    private final AccountStatus accountStatus;

    /** Device fingerprint of this login (optional). */
    private final String deviceFp;

    /** Device id = HMAC(salt, dfp + uid) (optional, mapped to the did claim). */
    private final String did;

    /** Role codes bound via user_role (Phase 2.5). */
    private final List<String> roleCodes;

    @JsonCreator
    public SsoUserPrincipal(
            @JsonProperty("uid") long uid,
            @JsonProperty("loginName") String loginName,
            @JsonProperty("nickname") String nickname,
            @JsonProperty("avatarUuid") String avatarUuid,
            @JsonProperty("userType") UserType userType,
            @JsonProperty("accountStatus") AccountStatus accountStatus,
            @JsonProperty("deviceFp") String deviceFp,
            @JsonProperty("did") String did,
            @JsonProperty("roleCodes") List<String> roleCodes) {
        this.uid = uid;
        this.loginName = loginName;
        this.nickname = nickname;
        this.avatarUuid = avatarUuid;
        this.userType = userType;
        this.accountStatus = accountStatus;
        this.deviceFp = deviceFp;
        this.did = did;
        this.roleCodes = roleCodes == null ? new ArrayList<>() : new ArrayList<>(roleCodes);
    }

    /** Builds the principal from the login user plus device and role context. */
    public static SsoUserPrincipal of(User user, String deviceFp, String did, List<String> roleCodes) {
        return new SsoUserPrincipal(
                user.getUid(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarResourceUuid(),
                user.getUserType(),
                user.getAccountStatus(),
                deviceFp,
                did,
                roleCodes);
    }

    /** Display name: nickname first, fallback to login name. */
    @JsonIgnore
    public String getDisplayName() {
        return (StringUtil.isNotBlank(nickname)) ? nickname : loginName;
    }

    /**
     * Role codes prefixed with {@code ROLE_}; defaults to {@code ROLE_USER} when none bound.
     *
     * <p>The returned value is emitted into the token claims and persisted inside the
     * {@code OAuth2Authorization} metadata, so it MUST be a concrete mutable list:
     * the Authorization Server's JDBC Jackson {@code PolymorphicTypeValidator} denies
     * {@code java.util.ImmutableCollections$*} (what {@code List.of} / {@code Stream.toList}
     * produce) on read-back.</p>
     */
    @JsonIgnore
    public List<String> getRoles() {
        List<String> prefixed = roleCodes.isEmpty()
                ? List.of("ROLE_USER")
                : roleCodes.stream()
                        .map(code -> code.startsWith("ROLE_") ? code : "ROLE_" + code)
                        .toList();
        LinkedHashSet<String> roles = new LinkedHashSet<>(prefixed);
        // SUPER_ADMIN (super admin / 站长) implicitly holds ADMIN, so every existing
        // hasAuthority('ROLE_ADMIN') guard keeps passing for a super admin without changes.
        if (roles.contains("ROLE_SUPER_ADMIN")) {
            roles.add("ROLE_ADMIN");
        }
        return new ArrayList<>(roles);
    }

    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Same constraint as getRoles(): keep the serialized authorities a concrete ArrayList.
        return new ArrayList<>(getRoles().stream().map(SimpleGrantedAuthority::new).toList());
    }

    @JsonIgnore
    @Override
    public String getPassword() {
        return null;
    }

    /** Returns uid so the OIDC sub equals uid. */
    @JsonIgnore
    @Override
    public String getUsername() {
        return String.valueOf(uid);
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonLocked() {
        return accountStatus != AccountStatus.SUSPENDED;
    }

    @JsonIgnore
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    /**
     * Identity is the account uid.
     *
     * <p>Spring Authorization Server's {@code SessionRegistryImpl} keys tracked OIDC sessions by
     * the principal object. The principal is persisted inside the {@code OAuth2Authorization}
     * (JDBC) and rehydrated as a <em>new</em> instance at the token endpoint, so without value
     * equality the registry lookup misses and the {@code id_token} is minted without a {@code sid}
     * claim — which then makes RP-Initiated Logout fail with {@code 400 invalid_token (sid)}.
     * Value equality by uid (the token {@code sub}) makes the lookup stable.</p>
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SsoUserPrincipal that)) {
            return false;
        }
        return uid == that.uid;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(uid);
    }
}
