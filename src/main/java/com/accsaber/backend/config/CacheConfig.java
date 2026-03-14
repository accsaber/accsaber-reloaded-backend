package com.accsaber.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

        public static final String LEADERBOARD_CACHE = "leaderboard";

        @Value("${accsaber.cache.leaderboard-ttl:300}")
        private long leaderboardTtl;

        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                ObjectMapper mapper = JsonMapper.builder()
                                .findAndAddModules()
                                .activateDefaultTyping(
                                                BasicPolymorphicTypeValidator.builder()
                                                                .allowIfBaseType(Object.class)
                                                                .build(),
                                                ObjectMapper.DefaultTyping.NON_FINAL)
                                .build();

                RedisSerializer<Object> serializer = new RedisSerializer<>() {
                        @Override
                        public byte[] serialize(Object value) {
                                if (value == null)
                                        return new byte[0];
                                try {
                                        return mapper.writeValueAsBytes(value);
                                } catch (IOException e) {
                                        throw new SerializationException("Could not serialize cache value", e);
                                }
                        }

                        @Override
                        public Object deserialize(byte[] bytes) {
                                if (bytes == null || bytes.length == 0)
                                        return null;
                                try {
                                        return mapper.readValue(bytes, Object.class);
                                } catch (IOException e) {
                                        throw new SerializationException("Could not deserialize cache value", e);
                                }
                        }
                };

                RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(leaderboardTtl))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(config)
                                .build();
        }
}
