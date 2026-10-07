package cn.nitrowater.account.security;

import cn.nitrowater.core.lib.entity.user.AccountStatus;
import cn.nitrowater.core.lib.entity.user.User;
import cn.nitrowater.core.lib.entity.user.UserType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * SSO 登录主体（Spring Security principal）。
 *
 * <p>承载完整身份上下文，供 OIDC 令牌 claim 映射：{@code preferred_username / name / roles / did}。</p>
 *
 * <p><b>为什么 {@link #getUsername()} 返回 uid</b>：Spring Authorization Server 默认以
 * {@code principal.getName()} 作为令牌的 {@code sub}。返回 uid 可保证 {@code sub = uid}，
 * 与 Phase 1 自研 JWT（{@code sub=uid}）及 WaterFun 网关注入的 {@code X-User-Uid} 对齐——这是
 * 迁移方案里 sub 语义的硬约束，不是随意选择。</p>
 *
 * <p>登录名（用户输入的用户名）另存于 {@link #loginName}，作为 {@code preferred_username} 输出。</p>
 */
@Getter
public class SsoUserPrincipal implements UserDetails {

    /** 账号 uid（Sub 语义）。 */
    private final long uid;

    /** 登录名（username），映射为 preferred_username claim。 */
    private final String loginName;

    private final String nickname;

    private final UserType userType;

    private final AccountStatus accountStatus;

    /** 本次登录的设备指纹（可选）。 */
    private final String deviceFp;

    /** 设备标识 did = HMAC(salt, dfp + uid)（可选，映射为 did claim）。 */
    private final String did;

    public SsoUserPrincipal(long uid, String loginName, String nickname,
                            UserType userType, AccountStatus accountStatus,
                            String deviceFp, String did) {
        this.uid = uid;
        this.loginName = loginName;
        this.nickname = nickname;
        this.userType = userType;
        this.accountStatus = accountStatus;
        this.deviceFp = deviceFp;
        this.did = did;
    }

    /** 从登录返回的用户实体构建，补上设备信息。 */
    public static SsoUserPrincipal of(User user, String deviceFp, String did) {
        return new SsoUserPrincipal(
                user.getUid(),
                user.getUsername(),
                user.getNickname(),
                user.getUserType(),
                user.getAccountStatus(),
                deviceFp,
                did);
    }

    /** 展示名：昵称优先，回落登录名。 */
    public String getDisplayName() {
        return (nickname != null && !nickname.isBlank()) ? nickname : loginName;
    }

    /** 角色集：始终含 {@code ROLE_USER}，管理/运营类按 userType 追加（不再写死单一角色）。 */
    public List<String> getRoles() {
        List<String> roles = new ArrayList<>();
        roles.add("ROLE_USER");
        if (userType != null && userType != UserType.COMMON) {
            roles.add("ROLE_" + userType.name());
        }
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return getRoles().stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    /** 刻意返回 uid —— 保证 OIDC {@code sub = uid}。 */
    @Override
    public String getUsername() {
        return String.valueOf(uid);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountStatus != AccountStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }
}
