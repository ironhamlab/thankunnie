package com.ucamp.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import com.ucamp.app.common.auth.StubAuthFilter;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (environment.acceptsProfiles(Profiles.of(STUB_AUTH_PROFILES))) {
            http.addFilterBefore(new StubAuthFilter(), AnonymousAuthenticationFilter.class);
        }
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
                        // 인증(JWT 등) 도입 전까지 전체 허용. 인증 이슈에서 authenticated() 로 변경한다
                        .anyRequest().permitAll());
        return http.build();
    }
}
