package cn.nitrowater.resource.application.dto;

import java.util.List;

/**
 * One page of the admin resource listing.
 *
 * <p>Keeps the application layer independent of the persistence pagination types: the web layer
 * renders this into its own wire shape.</p>
 *
 * @param items resources on this page
 * @param total total number of matching resources across all pages
 * @param page zero-based page index
 * @param size requested page size
 * @param totalPages total number of pages
 */
public record AdminResourcePage(
        List<ResourceMeta> items,
        long total,
        int page,
        int size,
        int totalPages) {
}
