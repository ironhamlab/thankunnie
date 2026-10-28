package com.ucamp.app.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import com.ucamp.app.common.auth.JwtAuthFilter;
import com.ucamp.app.common.auth.JwtProvider;
import com.ucamp.app.common.auth.StubAuthFilter;
import com.ucamp.app.common.util.TimeUtils;

/**
 * Spring Security 설정.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/h2-console/**"
    };

    // 테스트 사용자 stub 은 운영(prod)에서 동작하지 않도록 이 프로필에서만 등록한다
    private static final String[] STUB_AUTH_PROFILES = {"local", "test"};

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider) throws Exception {
        // stub 이 먼저 테스트 사용자를 인증하고, JWT 필터는 아직 인증되지 않은 요청만 처리한다
        if (environment.acceptsProfiles(Profiles.of(STUB_AUTH_PROFILES))) {
            http.addFilterBefore(new StubAuthFilter(), AnonymousAuthenticationFilter.class);
        }
        http.addFilterBefore(new JwtAuthFilter(jwtProvider), AnonymousAuthenticationFilter.class);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // H2 콘솔은 iframe 으로 동작하므로 같은 출처의 frame 을 허용한다
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 경로 단위로 막지 않는다. 인증은 @LoginUser(비로그인 401),
                        // 역할·매장 권한은 서비스의 requireRole·requireShopOwner(403)에서 검사한다
                        .anyRequest().permitAll());
        return http.build();
    }

    /**
     * 액세스 토큰 발급·검증기. @WebMvcTest 처럼 TimeConfig 가 없는 슬라이스 테스트에서도
     * 이 설정만 Import 하면 동작하도록 Clock 이 없으면 한국 시간 시스템 시계를 쓴다.
     */
    @Bean
    public JwtProvider jwtProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-validity:PT24H}") Duration accessTokenValidity,
            ObjectProvider<Clock> clockProvider) {
        Clock clock = clockProvider.getIfAvailable(() -> Clock.system(TimeUtils.KST));
        return new JwtProvider(secret, accessTokenValidity, clock);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
