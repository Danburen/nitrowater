package cn.nitrowater.resource.web.dto;

import java.util.List;

/**
 * A page of wire items plus its pagination cursors, shared by admin listings.
 *
 * @param items items on this page
 * @param total total number of matching rows across all pages
 * @param page zero-based page index
 * @param size requested page size
 * @param totalPages total number of pages
 * @param <T> item view type
 */
public record PageResp<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages) {
}
