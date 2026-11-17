package cn.nitrowater.core.infrastructure.utils.context;


import org.jetbrains.annotations.Nullable;
import cn.nitrowater.core.infrastructure.domain.user.User;

import java.util.Locale;
import java.util.Optional;

/**
 * Legacy request-scoped identity holder (account-module adapter).
 *
 * <p>New code reads identity through
 * {@link cn.nitrowater.core.infrastructure.security.CurrentUser}, which works off Spring
 * Security's {@code SecurityContext} and owns the single uid rule ({@code uid} claim, then
 * {@code sub}). This ThreadLocal exists only because the legacy account services still read it:
 * the account filter fills it per request and always clears it in a {@code finally}.</p>
 *
 * <p>Do not add new dependencies on it, and never let it escape the request thread — async/SSE
 * work has no context here (use {@code SecurityContext} propagation instead).</p>
 */
public class UserCtxHolder {
    private static final ThreadLocal<AuthContext> THREAD_LOCAL = new ThreadLocal<>();
    public static AuthContext get(){
        return THREAD_LOCAL.get();
    }
    public static Optional<Long> safeGetUserId() {
        return Optional.ofNullable(get()).map(AuthContext::getUserUid);
    }
    public static Optional<AuthContext> safeGet() {
        return Optional.ofNullable(get());
    }

    /**
     * This method only allow used in authenticated scene
     * we recommend using {@link UserCtxHolder#safeGetUserId()}
     * <p>Must not be used when an anonymous may pass such as auth interface</p>
     * @return {@link User#getUid()}
     */
    public static Long getUserUid(){
        return get().getUserUid();
    }

    /**
     * This method can be called when an anonymous request or authenticated request
     * will return null if the request is anonymous.
     * @return {@link User#getUid()} or null if the request is anonymous
     */
    @Nullable
    public static Long unsafeGetUserUid(){
        return get() == null ? null : get().getUserUid();
    }
    /**
     * Set value to ThreadLocal
     */
    public static void set(AuthContext context){
        THREAD_LOCAL.set(context);
    }



    /**
     * Get jwt identify.
     * @return the java web token id.
     */
    public static String getJti(){
        return get().getJti();
    }

    /**
     * Remoce ThreadLocal to avoid memory leak.
     */
    public static void remove(){
        THREAD_LOCAL.remove();
    }

    public static Locale getLocale() {
        return get().getLocale();
    }

    public static String getClientIp() {
        return get().getClientIp();
    }
}
