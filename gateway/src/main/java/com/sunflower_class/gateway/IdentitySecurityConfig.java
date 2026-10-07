package com.sunflower_class.gateway;

import java.util.Base64;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

/** 网关验证浏览器 Cookie 或服务调用的 Bearer，业务服务仍再次验签，不信任伪造身份头。 */
@Configuration
@EnableWebFluxSecurity
public class IdentitySecurityConfig {

    /** 与业务服务使用同一外部密钥及签发者，并检查令牌有效期。 */
    @Bean
    public NimbusReactiveJwtDecoder jwtDecoder(@Value("${sunflower.auth.secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) throw new IllegalArgumentException("认证密钥至少需要 256 位");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(
            new SecretKeySpec(bytes, "HmacSHA256")
        ).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("sunflower-class"));
        return decoder;
    }

    /** CSRF 在接收写操作的业务服务校验；网关不创建 Session，也不提供表单登录。 */
    @Bean
    public SecurityWebFilterChain identityFilterChain(ServerHttpSecurity http) {
        ServerBearerTokenAuthenticationConverter bearer =
            new ServerBearerTokenAuthenticationConverter();
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange(auth ->
                auth
                    .pathMatchers(
                        HttpMethod.GET,
                        "/auth/csrf",
                        "/search/courses/**",
                        "/search/categories",
                        "/learning/courses/*/directory",
                        "/content/published-courses/**",
                        "/media/files/*/content",
                        // 只开放试学专用GET入口，普通播放和选课仍需登录。
                        "/media/trial/*/*",
                        "/learning/trial/*/*"
                    )
                    .permitAll()
                    .pathMatchers(
                        HttpMethod.POST,
                        "/auth/login",
                        "/auth/logout",
                        "/auth/register",
                        "/orders/payments/alipay/notify"
                    )
                    .permitAll()
                    .anyExchange()
                    .authenticated()
            )
            .oauth2ResourceServer(resource ->
                resource
                    .bearerTokenConverter(exchange ->
                        bearer.convert(exchange).switchIfEmpty(
                            Mono.defer(() -> {
                                if (
                                    Set.of(
                                        "/auth/login",
                                        "/auth/csrf",
                                        "/auth/logout",
                                        "/auth/register",
                                        "/orders/payments/alipay/notify"
                                    ).contains(exchange.getRequest().getPath().value()) ||
                                    (HttpMethod.GET.equals(exchange.getRequest().getMethod()) &&
                                        (exchange
                                            .getRequest()
                                            .getPath()
                                            .value()
                                            .startsWith("/media/trial/") ||
                                            exchange
                                                .getRequest()
                                                .getPath()
                                                .value()
                                                .startsWith("/learning/trial/")))
                                ) return Mono.empty();
                                HttpCookie cookie = exchange
                                    .getRequest()
                                    .getCookies()
                                    .getFirst("SUNFLOWER_TOKEN");
                                return cookie == null
                                    ? Mono.empty()
                                    : Mono.just(
                                          new BearerTokenAuthenticationToken(cookie.getValue())
                                      );
                            })
                        )
                    )
                    .jwt(Customizer.withDefaults())
            )
            .build();
    }
}
