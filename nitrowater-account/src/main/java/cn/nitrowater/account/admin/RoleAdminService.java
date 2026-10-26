package cn.nitrowater.account.admin;

import cn.nitrowater.account.admin.dto.AssignRolesReq;
import cn.nitrowater.account.admin.dto.PageResult;
import cn.nitrowater.account.admin.dto.RoleCreateReq;
import cn.nitrowater.account.admin.dto.RoleUpdateReq;
import cn.nitrowater.account.admin.dto.RoleVO;
import cn.nitrowater.account.admin.dto.UserSummaryVO;
import cn.nitrowater.core.domain.role.Role;
import cn.nitrowater.core.domain.role.UserRole;
import cn.nitrowater.core.api.event.UserSessionsInvalidatedEvent;
import cn.nitrowater.core.domain.user.User;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.core.infrastructure.persistence.role.RoleRepository;
import cn.nitrowater.core.infrastructure.persistence.role.UserRoleRepository;
import cn.nitrowater.core.infrastructure.persistence.user.UserRepository;
import cn.nitrowater.lib.api.BaseResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin console service for role CRUD and user-role assignment.
 *
 * <p>Lives in {@code nitrowater-account} because the {@code role} / {@code user_role} tables are owned
 * by the SSO database. The console is a thin, transactional facade over
 * {@link RoleRepository} / {@link UserRoleRepository}; all authorization (only
 * {@code ROLE_SUPER_ADMIN} may reach it) is enforced by the Servlet security chain, not here.</p>
 *
 * <p>Guards:</p>
 * <ul>
 *   <li>built-in roles ({@code USER}/{@code ADMIN}/{@code SUPER_ADMIN}) cannot be deleted;</li>
 *   <li>a role still bound to accounts cannot be deleted;</li>
 *   <li>the last {@code SUPER_ADMIN} binding can never be removed (lock-out protection).</li>
 * </ul>
 *
 * <p>Replacing an account's role set also invalidates that account's sessions: the {@code roles}
 * claim is a snapshot taken at login, so an old session would otherwise keep its old authorities for
 * the refresh-token lifetime.</p>
 */
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    /** The super-admin role code; its last binding is protected from removal. */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------ roles

    /** All roles with their bound-account counts, ordered built-in first then by code. */
    @Transactional(readOnly = true)
    public List<RoleVO> listRoles() {
        return roleRepository.findAll(Sort.by(Sort.Direction.DESC, "builtin").and(Sort.by("code")))
                .stream()
                .map(this::toRoleVO)
                .toList();
    }

    /** Creates a non-built-in role; the code is normalized to upper-case and must be unique. */
    @Transactional
    public RoleVO createRole(RoleCreateReq req) {
        String code = normalizeCode(req.code());
        String name = req.name().trim();
        if (roleRepository.existsByCode(code)) {
            throw new BizException(BaseResponseCode.ROLE_ALREADY_EXISTS, code);
        }
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setDescription(blankToNull(req.description()));
        role.setBuiltin(false);
        return toRoleVO(roleRepository.save(role));
    }

    /** Updates a role's display name / description. The code is immutable. */
    @Transactional
    public RoleVO updateRole(long id, RoleUpdateReq req) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BizException(BaseResponseCode.ROLE_NOT_FOUND, id));
        role.setName(req.name().trim());
        role.setDescription(blankToNull(req.description()));
        return toRoleVO(roleRepository.save(role));
    }

    /** Deletes a role unless it is built-in or still assigned to accounts. */
    @Transactional
    public void deleteRole(long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BizException(BaseResponseCode.ROLE_NOT_FOUND, id));
        if (Boolean.TRUE.equals(role.getBuiltin())) {
            throw new BizException(BaseResponseCode.CAN_NOT_DELETE_SYS_ROLE);
        }
        if (userRoleRepository.countByRoleId(id) > 0) {
            throw new BizException("admin.role.in_use", "admin.role.in_use");
        }
        roleRepository.delete(role);
    }

    // ------------------------------------------------------------------ users

    /** Paged account search (username / nickname substring) with each account's bound role codes. */
    @Transactional(readOnly = true)
    public PageResult<UserSummaryVO> listUsers(String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<User> result = userRepository.searchByKeyword(kw, pageable);
        List<Long> uids = result.getContent().stream().map(User::getUid).toList();
        Map<Long, List<String>> rolesByUid = rolesByUid(uids);

        List<UserSummaryVO> rows = result.getContent().stream()
                .map(user -> toUserSummary(user, rolesByUid.getOrDefault(user.getUid(), List.of())))
                .toList();
        return PageResult.of(rows, result.getTotalElements(), safePage, safeSize);
    }

    /** Role codes currently bound to one account (dialog pre-fill). */
    @Transactional(readOnly = true)
    public List<String> getUserRoles(long uid) {
        if (!userRepository.existsById(uid)) {
            throw new BizException(BaseResponseCode.USER_NOT_FOUND);
        }
        return userRoleRepository.findRoleCodesByUid(uid);
    }

    /**
     * Replaces the account's role set with {@code roleCodes}.
     *
     * <p>Rejects unknown codes and refuses to strip the last {@code SUPER_ADMIN} binding.</p>
     */
    @Transactional
    public List<String> setUserRoles(long uid, AssignRolesReq req) {
        User user = userRepository.findUserByUid(uid)
                .orElseThrow(() -> new BizException(BaseResponseCode.USER_NOT_FOUND));

        Set<String> desired = normalizeCodes(req.roleCodes());
        Map<String, Role> rolesByCode = resolveRoles(desired);

        Set<String> current = new LinkedHashSet<>(userRoleRepository.findRoleCodesByUid(uid));
        if (current.contains(SUPER_ADMIN) && !desired.contains(SUPER_ADMIN)
                && userRoleRepository.countByRoleCode(SUPER_ADMIN) <= 1) {
            throw new BizException(BaseResponseCode.CAN_NOT_UNSET_SYS_ROLE);
        }

        userRoleRepository.deleteByUid(user.getUid());
        userRoleRepository.flush();
        List<UserRole> bindings = new ArrayList<>(desired.size());
        for (String code : desired) {
            UserRole binding = new UserRole();
            binding.setUid(user.getUid());
            binding.setRoleId(rolesByCode.get(code).getId());
            bindings.add(binding);
        }
        userRoleRepository.saveAll(bindings);
        // The roles claim is snapshotted in every issued token (and in the stored Authorization), so
        // a changed binding only takes effect once the old sessions are destroyed.
        eventPublisher.publishEvent(new UserSessionsInvalidatedEvent(user.getUid(),
                UserSessionsInvalidatedEvent.Reason.ROLES_CHANGED));
        return new ArrayList<>(desired);
    }

    // ------------------------------------------------------------------ helpers

    private Map<Long, List<String>> rolesByUid(Collection<Long> uids) {
        if (uids.isEmpty()) {
            return Map.of();
        }
        return userRoleRepository.findByUidInWithRole(uids).stream()
                .collect(Collectors.groupingBy(UserRole::getUid,
                        Collectors.mapping(ur -> ur.getRole().getCode(), Collectors.toList())));
    }

    private Map<String, Role> resolveRoles(Set<String> codes) {
        Map<String, Role> byCode = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getCode, role -> role));
        for (String code : codes) {
            if (!byCode.containsKey(code)) {
                throw new BizException(BaseResponseCode.ROLE_NOT_FOUND, code);
            }
        }
        return byCode;
    }

    private RoleVO toRoleVO(Role role) {
        long userCount = userRoleRepository.countByRoleId(role.getId());
        return new RoleVO(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getBuiltin(), userCount, role.getCreatedAt());
    }

    private UserSummaryVO toUserSummary(User user, List<String> roles) {
        return new UserSummaryVO(user.getUid(), user.getUsername(), user.getNickname(),
                user.getAccountStatus() != null ? user.getAccountStatus().name() : null,
                user.getUserType() != null ? user.getUserType().name() : null,
                roles, user.getCreatedAt());
    }

    /** Upper-cases and trims a role code ({@code ROLE_} prefix stripped if present). */
    private static String normalizeCode(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (code.startsWith("ROLE_")) {
            code = code.substring("ROLE_".length());
        }
        return code;
    }

    private static Set<String> normalizeCodes(List<String> raw) {
        Set<String> codes = new LinkedHashSet<>();
        if (raw != null) {
            for (String value : raw) {
                if (value != null && !value.isBlank()) {
                    codes.add(normalizeCode(value));
                }
            }
        }
        return codes;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
