package cn.nitrowater.server.service.sponsor;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.config.IfdianProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Serves sponsor pages from Redis, fetching a page from ifdian only on a cache miss.
 *
 * <p>Only the projected fields live in the cache ({@code nitrowater:cache:sponsor:v2:page:N}, 18 by
 * default, six-hour TTL). {@link SponsorRefreshScheduler} warms page one proactively; every other
 * page is loaded lazily the first time the About page asks for it. A failed upstream call never
 * makes the public endpoint fail: an existing cached page is served as-is, otherwise an empty page
 * is returned and the next request may retry.</p>
 */
@Service
public class SponsorService {

    private static final Logger log = LoggerFactory.getLogger(SponsorService.class);
    // Versioned so a field/page-size change never serves stale entries from the previous shape.
    private static final String CACHE_KEY_PREFIX = "nitrowater:cache:sponsor:v2:page:";

    private final IfdianClient client;
    private final StringRedisTemplate redis;
    private final IfdianProperties properties;

    /**
     * @param client signed ifdian client
     * @param redis cache storage (the server's Redis, not the BFF session namespace)
     * @param properties ifdian credentials and cache settings
     */
    public SponsorService(IfdianClient client, StringRedisTemplate redis, IfdianProperties properties) {
        this.client = client;
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * @param requested zero-based page number from the browser
     * @return one page of sponsors, empty when unconfigured or the upstream is unavailable
     */
    public Page<SponsorView> page(int requested) {
        int page = Math.max(0, requested);
        int size = properties.size();
        SponsorPageCache cached = read(page);
        if (cached != null) {
            return toPage(cached, page, size);
        }
        if (!properties.configured()) {
            return empty(page, size);
        }
        try {
            // ifdian numbers pages from one; the browser contract is zero-based.
            SponsorPageCache fresh = fetch(page + 1, size);
            write(page, fresh);
            return toPage(fresh, page, size);
        } catch (Exception ex) {
            log.warn("Failed to load ifdian sponsors for page {}: {}", page, ex.toString());
            return empty(page, size);
        }
    }

    /**
     * Warms only the first page; deeper pages stay lazy so a six-hour tick cannot hammer ifdian.
     */
    public void refreshFirstPage() {
        if (!properties.configured()) {
            return;
        }
        try {
            write(0, fetch(1, properties.size()));
        } catch (Exception ex) {
            log.warn("Scheduled ifdian sponsor refresh failed: {}", ex.toString());
        }
    }

    /**
     * @param ifdianPage one-based ifdian page
     * @param size page size
     * @return the projected cache payload
     */
    private SponsorPageCache fetch(int ifdianPage, int size) {
        JsonNode data = client.querySponsors(ifdianPage, size).path("data");
        List<SponsorView> sponsors = new ArrayList<>();
        JsonNode list = data.path("list");
        if (list.isArray()) {
            for (JsonNode item : list) {
                sponsors.add(toView(item));
            }
        }
        // ifdian reports total_count; the older "total" key stays as a test/fallback shape.
        long total = data.path("total_count").asLong(data.path("total").asLong(sponsors.size()));
        return new SponsorPageCache(List.copyOf(sponsors), total);
    }

    /**
     * Maps one ifdian sponsor to the fields the UI displays, tolerating both snake_case and camelCase.
     */
    private static SponsorView toView(JsonNode item) {
        JsonNode user = item.path("user");
        String nickname = firstText(user, "name", "nickname");
        if (nickname.isEmpty()) {
            nickname = firstText(item, "nickname", "name");
        }
        String avatar = firstText(user, "avatar", "avatar_url", "avatarUrl");
        if (avatar.isEmpty()) {
            avatar = firstText(item, "avatar", "avatar_url");
        }
        return new SponsorView(
                firstText(user, "user_id", "id"),
                nickname,
                avatar,
                firstText(item, "last_pay_time", "lastPayTime", "last_sponsor_time"));
    }

    /**
     * @param node object to read
     * @param names accepted field names, in priority order
     * @return the first non-blank text value, or an empty string
     */
    private static String firstText(JsonNode node, String... names) {
        if (node == null || node.isNull() || !node.isObject()) {
            return "";
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    /**
     * @param page zero-based page number
     * @return cached payload, or {@code null} when the key is absent or unreadable
     */
    private SponsorPageCache read(int page) {
        try {
            String json = redis.opsForValue().get(CACHE_KEY_PREFIX + page);
            return json == null ? null : JsonUtil.fromJson(json, SponsorPageCache.class);
        } catch (Exception ex) {
            log.warn("Unreadable sponsor cache at page {}: {}", page, ex.toString());
            return null;
        }
    }

    /**
     * @param page zero-based page number
     * @param value payload to cache
     */
    private void write(int page, SponsorPageCache value) {
        try {
            redis.opsForValue().set(CACHE_KEY_PREFIX + page, JsonUtil.toJson(value), properties.ttl());
        } catch (Exception ex) {
            // A cache outage must not hide fresh data from the caller.
            log.warn("Failed to cache sponsors for page {}: {}", page, ex.toString());
        }
    }

    /**
     * @param cached cached payload
     * @param page zero-based page number
     * @param size page size
     * @return the standard Spring Data page shape used by every other list endpoint
     */
    private static Page<SponsorView> toPage(SponsorPageCache cached, int page, int size) {
        return new PageImpl<>(cached.content(), PageRequest.of(page, size), cached.total());
    }

    /**
     * @param page zero-based page number
     * @param size page size
     * @return an empty page with the same wire shape as a populated one
     */
    private static Page<SponsorView> empty(int page, int size) {
        return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
    }
}
