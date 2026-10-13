package cn.nitrowater.account.admin.dto;

import java.util.List;

/**
 * Lightweight pagination envelope for the admin console (list + total + current page/size).
 *
 * <p>Kept independent of Spring Data's {@code Page} so the JSON shape stays stable and free of
 * framework internals when the SPA consumes it.</p>
 *
 * @param <T> row type
 */
public record PageResult<T>(List<T> list, long total, int page, int size) {

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }
}
