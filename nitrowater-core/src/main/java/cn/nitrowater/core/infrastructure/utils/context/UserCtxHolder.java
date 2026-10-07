package cn.nitrowater.core.infrastructure.utils.context;


import org.jetbrains.annotations.Nullable;
import cn.nitrowater.core.entity.user.User;

import java.util.Locale;
import java.util.Optional;

/**
 * ThreadLocal Util
 *
 * Use Spring Security instance
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
