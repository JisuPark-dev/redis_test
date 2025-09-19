package com.study.redis_test.config;

import java.io.IOException;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;
    
    @Bean
    public RedissonClient redissonClient() throws IOException {
        Config config = Config.fromYAML(
            "singleServerConfig:\n" +
            "  address: redis://" + redisHost + ":" + redisPort + "\n" +
            "  connectionPoolSize: 64\n" +
            "  connectionMinimumIdleSize: 24\n" +
            "  subscriptionConnectionPoolSize: 50\n" +
            "  subscriptionConnectionMinimumIdleSize: 1\n" +
            "  timeout: 3000\n" +
            "  retryAttempts: 3\n" +
            "  retryInterval: 1500\n"
        );
        return Redisson.create(config);
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}