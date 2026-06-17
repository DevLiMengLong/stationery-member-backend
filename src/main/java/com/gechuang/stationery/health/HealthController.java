package com.gechuang.stationery.health;

import com.gechuang.stationery.common.RestResponse;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping
    public RestResponse<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", "stationery-member-backend");
        result.put("status", "UP");
        result.put("time", LocalDateTime.now().toString());
        result.put("redis", pingRedis());
        return RestResponse.success(result);
    }

    private String pingRedis() {
        try {
            String pong = redisConnectionFactory.getConnection().ping();
            return pong == null ? "UNKNOWN" : pong;
        } catch (Exception exception) {
            return "DOWN";
        }
    }
}
