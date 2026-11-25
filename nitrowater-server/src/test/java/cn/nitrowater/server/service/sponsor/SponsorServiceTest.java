package cn.nitrowater.server.service.sponsor;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.config.IfdianProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the cache contract: cache hits skip ifdian, misses fetch and store only the projected
 * fields, and upstream/unconfigured failures never break the anonymous endpoint.
 */
class SponsorServiceTest {

    private static final String PAGE_ZERO_KEY = "nitrowater:cache:sponsor:v2:page:0";

    private final IfdianClient client = mock(IfdianClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final IfdianProperties properties = new IfdianProperties(
            "https://ifdian.net/api/open", "ifdian-user", "secret-token",
            3000, 10000, 18, Duration.ofHours(6), 21600000L);

    private SponsorService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        service = new SponsorService(client, redis, properties);
    }

    @Test
    void fetchesAndCachesOnlyProjectedFieldsOnMiss() {
        when(client.querySponsors(1, 18)).thenReturn(ifdianPage());

        Page<SponsorView> page = service.page(0);

        assertEquals(2, page.getContent().size());
        assertEquals("小明", page.getContent().get(0).nickname());
        assertEquals("https://example.com/a.png", page.getContent().get(0).avatarUrl());
        assertEquals("2026-11-01 12:00:00", page.getContent().get(0).lastSponsorTime());
        assertEquals(42, page.getTotalElements());
        assertEquals(0, page.getNumber());
        assertEquals(18, page.getSize());
        assertEquals(3, page.getTotalPages());
        verify(values).set(eq(PAGE_ZERO_KEY), anyString(), eq(Duration.ofHours(6)));
    }

    @Test
    void servesCachedPageWithoutCallingUpstream() {
        SponsorPageCache cached = new SponsorPageCache(
                List.of(new SponsorView("u1", "缓存赞助者", "", "2026-11-01")), 1);
        when(values.get(PAGE_ZERO_KEY)).thenReturn(JsonUtil.toJson(cached));

        Page<SponsorView> page = service.page(0);

        assertEquals("缓存赞助者", page.getContent().getFirst().nickname());
        assertEquals(1, page.getTotalElements());
        verifyNoInteractions(client);
    }

    @Test
    void translatesZeroBasedRequestToIfdianOneBasedPage() {
        when(client.querySponsors(3, 18)).thenReturn(ifdianPage());

        service.page(2);

        verify(client).querySponsors(3, 18);
    }

    @Test
    void returnsEmptyPageWhenUpstreamFailsAndNothingIsCached() {
        when(client.querySponsors(1, 18)).thenThrow(new IllegalStateException("ifdian 400004"));

        Page<SponsorView> page = service.page(0);

        assertTrue(page.isEmpty());
        assertEquals(0, page.getTotalElements());
        verify(values, never()).set(eq(PAGE_ZERO_KEY), anyString(), eq(Duration.ofHours(6)));
    }

    @Test
    void blankCredentialsDisableUpstreamCalls() {
        IfdianProperties blank = new IfdianProperties(
                "https://ifdian.net/api/open", "", "",
                3000, 10000, 18, Duration.ofHours(6), 21600000L);
        SponsorService disabled = new SponsorService(client, redis, blank);

        Page<SponsorView> page = disabled.page(0);

        assertTrue(page.isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void scheduledRefreshOnlyWarmsTheFirstPage() {
        when(client.querySponsors(1, 18)).thenReturn(ifdianPage());

        service.refreshFirstPage();

        verify(client).querySponsors(1, 18);
        verify(values).set(eq(PAGE_ZERO_KEY), anyString(), eq(Duration.ofHours(6)));
    }

    /**
     * @return a representative ifdian envelope with snake_case fields and nested user/plan objects
     */
    private static JsonNode ifdianPage() {
        return JsonUtil.getMapper().readTree("""
                {"ec":200,"em":"success","data":{"list":[
                  {"all_sum_amount":"88.00","last_pay_time":"2026-11-01 12:00:00",
                   "user":{"user_id":"u1","name":"小明","avatar":"https://example.com/a.png"},
                   "current_plan":{"name":"赞助者"}},
                  {"all_sum_amount":"6.00","last_pay_time":"2026-10-02 09:00:00",
                   "nickname":"匿名朋友","sponsor_plans":[{"name":"支持者"}]}
                ],"total_count":42}}
                """);
    }
}
