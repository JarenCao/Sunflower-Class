package com.sunflower_class.base.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

/** 所有业务服务独立验签，直接访问服务端口也不能绕过登录；公开课程允许匿名浏览。 */
@Configuration
public class IdentitySecurityConfig {

    /** 密钥只从外部配置读取，不提供写死的开发密钥。 */
    @Bean
    public SecretKeySpec identityKey(@Value("${sunflower.auth.secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) throw new IllegalArgumentException("认证密钥至少需要 256 位");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** 框架同时检查签名、过期时间和签发者。 */
    @Bean
    public JwtDecoder jwtDecoder(SecretKeySpec identityKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(identityKey).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("sunflower-class"));
        return decoder;
    }

    /** Cookie 用于浏览器，Bearer 用于服务间调用；Cookie 请求必须通过 CSRF 校验。 */
    @Bean
    public SecurityFilterChain identityFilterChain(
        HttpSecurity http,
        @Value("${sunflower.auth.cookie-secure:true}") boolean secureCookie
    ) throws Exception {
        DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
        // 仅从已验签的 role 属性授权，身份头和 scope 不替代业务角色。
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter identity = new JwtAuthenticationConverter();
        identity.setJwtGrantedAuthoritiesConverter(roles);
        // Vite 为服务增加 /api 前缀，CSRF Cookie 必须覆盖代理路径和两个业务服务。
        CookieCsrfTokenRepository csrfCookies = new CookieCsrfTokenRepository();
        csrfCookies.setCookiePath("/");
        csrfCookies.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secureCookie));
        CsrfFilter csrfFilter = new CsrfFilter(csrfCookies);
        // 支付平台没有浏览器CSRF令牌，仅精确豁免验签回调，其他写请求维持原校验。
        csrfFilter.setRequireCsrfProtectionMatcher(
            request ->
                CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request) &&
                !(
                    "POST".equals(request.getMethod()) &&
                    "/payments/alipay/notify".equals(request.getServletPath())
                )
        );
        http.sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
            .cors(Customizer.withDefaults())
            // Resource Server 会自动豁免 Bearer 请求；Cookie JWT 不能套用该豁免。
            // 单独放入原生 CSRF 过滤器，所有写请求都必须携带与 Cookie 配对的令牌。
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(csrfFilter, LogoutFilter.class)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth ->
                auth
                    // 保留原生 CSRF 的 403；错误分派不能再被当作匿名管理请求改为 401。
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/payments/alipay/notify")
                    .permitAll()
                    // 用户确认允许匿名试学，仅放行只读的专用两级编号入口；业务层核对正式快照。
                    .requestMatchers(HttpMethod.GET, "/trial/*/*")
                    .permitAll()
                    .requestMatchers("/purchases", "/purchases/**")
                    .hasRole("student")
                    // 选课记录与资格只允许当前学生访问，不能被公开课程规则放行。
                    .requestMatchers("/enrollments", "/enrollments/**", "/playback/**")
                    .hasRole("student")
                    .requestMatchers("/auth/platform/**")
                    .hasRole("admin")
                    .requestMatchers("/auth/institution-applications")
                    .hasRole("student")
                    .requestMatchers("/auth/teachers", "/auth/teachers/**")
                    .hasRole("admin")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/auth/csrf",
                        "/courses/**",
                        "/categories",
                        "/published-courses/**",
                        "/files/*/content"
                    )
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/auth/login",
                        "/auth/logout",
                        "/auth/register"
                    )
                    .permitAll()
                    // 审核独立于机构维护，机构不能审核自己的课程。
                    .requestMatchers(
                        "/courseaudit/review/**",
                        "/courseaudit/queue",
                        "/courseaudit/detail/**"
                    )
                    .hasRole("admin")
                    .requestMatchers("/courseaudit/history/**")
                    .hasAnyRole("teacher", "admin")
                    .requestMatchers(
                        "/course",
                        "/course/**",
                        "/teachplan/**",
                        "/techplan/**",
                        "/courseaudit/commit/**",
                        "/coursepublish/**",
                        "/publication-messages/**",
                        "/files",
                        "/files/**",
                        "/upload/**"
                    )
                    .hasRole("teacher")
                    .anyRequest()
                    .authenticated()
            )
            .oauth2ResourceServer(resource ->
                resource
                    .bearerTokenResolver(request -> {
                        // 过期的旧 Cookie 不能阻止重新登录或清除登录；写入口仍校验 CSRF。
                        if (
                            Set.of(
                                "/auth/login",
                                "/auth/csrf",
                                "/auth/logout",
                                "/auth/register",
                                "/payments/alipay/notify"
                            ).contains(request.getServletPath()) ||
                            ("GET".equals(request.getMethod()) &&
                                request.getServletPath().startsWith("/trial/"))
                        ) return null;
                        String token = bearer.resolve(request);
                        if (token != null) return token;
                        if (request.getCookies() != null) {
                            for (Cookie cookie : request.getCookies()) {
                                if (
                                    "SUNFLOWER_TOKEN".equals(cookie.getName())
                                ) return cookie.getValue();
                            }
                        }
                        return null;
                    })
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(identity))
            );
        return http.build();
    }
}
