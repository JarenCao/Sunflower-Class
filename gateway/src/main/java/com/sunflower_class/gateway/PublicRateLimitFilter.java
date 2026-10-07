package com.sunflower_class.gateway;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 公开接口按真实连接 IP 限流；不信任客户端伪造的转发头，也不记录密码和令牌。 */
@Component
public class PublicRateLimitFilter implements GlobalFilter, Ordered {

    @Autowired
    private ReactiveStringRedisTemplate redis;

    // 原子计数并设置过期，所有网关实例共用同一窗口；拒绝请求不延长窗口。
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",
        Long.class
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String group;
        int maximum;
        if ("/auth/login".equals(path)) {
            group = "login";
            maximum = 20;
        } else if ("/auth/register".equals(path)) {
            group = "register";
            maximum = 5;
        } else if (path.startsWith("/search/")) {
            group = "search";
            maximum = 2400;
        } else if (path.startsWith("/media/trial/") || path.startsWith("/learning/trial/")) {
            group = "trial";
            maximum = 120;
        } else {
            return chain.filter(exchange);
        }
        if (exchange.getRequest().getRemoteAddress() == null) return reject(
            exchange,
            HttpStatus.SERVICE_UNAVAILABLE,
            "暂时无法确认请求来源，请稍后重试"
        );
        String address = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String key = "sunflower:rate:" + group + ":" + address;
        // 只对 Redis 计数处理故障，后续业务错误不能被误写为限流故障。
        return redis
            .execute(LIMIT, List.of(key), List.of("60"))
            .next()
            .timeout(java.time.Duration.ofSeconds(1))
            .onErrorReturn(-1L)
            .flatMap(count -> {
                if (count < 0) return reject(
                    exchange,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "访问保护暂不可用，请稍后重试"
                );
                if (count > maximum) {
                    exchange.getResponse().getHeaders().set("Retry-After", "60");
                    return reject(
                        exchange,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "请求过于频繁，请稍后重试"
                    );
                }
                return chain.filter(exchange);
            });
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"errMessage\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return exchange
            .getResponse()
            .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
