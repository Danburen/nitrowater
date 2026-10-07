package cn.nitrowater.account.security;

import cn.nitrowater.core.entity.user.AccountStatus;
import cn.nitrowater.core.entity.user.User;
import cn.nitrowater.core.entity.user.UserType;
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
import java.util.List;

/**
 * Phase 2.4: SSO login principal.
 *
 * <p>Carries the identity context used by token customization
 * ({@code preferred_username / name / roles / did}).</p>
 *
 * <p>Stored by Spring Authorization Server's JDBC store (default typing), so it must be
 * Jackson-constructible: the creator + JsonIgnore'd computed getters keep the persisted
 * JSON to the 8 fields below.</p>
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
            @JsonProperty("userType") UserType userType,
            @JsonProperty("accountStatus") AccountStatus accountStatus,
            @JsonProperty("deviceFp") String deviceFp,
            @JsonProperty("did") String did,
            @JsonProperty("roleCodes") List<String> roleCodes) {
        this.uid = uid;
        this.loginName = loginName;
        this.nickname = nickname;
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
                user.getUserType(),
                user.getAccountStatus(),
                deviceFp,
                did,
                roleCodes);
    }

    /** Display name: nickname first, fallback to login name. */
    @JsonIgnore
    public String getDisplayName() {
        return (nickname != null && !nickname.isBlank()) ? nickname : loginName;
    }

    /** Authorities: ROLE_ prefixed role codes; defaults to ROLE_USER when none bound. */
    @JsonIgnore
    public List<String> getRoles() {
        if (roleCodes.isEmpty()) {
            return List.of("ROLE_USER");
        }
        return roleCodes.stream()
                .map(code -> code.startsWith("ROLE_") ? code : "ROLE_" + code)
                .distinct()
                .toList();
    }

    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return getRoles().stream().map(SimpleGrantedAuthority::new).toList();
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
}
