package cn.nitrowater.core.configuration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import cn.nitrowater.core.common.RedisKeyPrefix;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfiguration {

    private static final String CACHE_KEY_PREFIX = RedisKeyPrefix.ROOT_CACHE;

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(CACHE_KEY_PREFIX)
                .entryTtl(Duration.ofHours(1))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer())
                );
    }

    private static GenericJacksonJsonRedisSerializer valueSerializer() {
        PolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                // NOTE: prefix matches by class-name; entities live under cn.nitrowater.core.*
                .allowIfSubType("cn.nitrowater.core.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("org.springframework.cache.support.NullValue")
                .build();

        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(validator)
                .enableSpringCacheNullValueSupport()
                .build();
    }
}
