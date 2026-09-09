package com.skilltracker.security;

import com.skilltracker.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Reproduces the endpoint-level protection of the FastAPI routers: most read endpoints are public
 * and only the routes that previously declared a {@code CurrentUser} dependency require a token.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    /**
     * passlib hashed with 12 rounds; keeping the same cost means new hashes are indistinguishable
     * from the ones already stored.
     */
    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, UserRepository userRepository, ObjectMapper objectMapper)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .anonymous(anonymous -> anonymous.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.requestMatchers(HttpMethod.GET, "/api/v1/auth/me")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users", "/api/v1/users/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/workspace-profile")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/tasks", "/api/v1/tasks/analyze")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/tasks/*/approve")
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.POST, "/api/v1/tasks/*/attachments", "/api/v1/tasks/*/attachments/upload")
                        .authenticated()
                        .requestMatchers("/api/v1/teams", "/api/v1/teams/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/integrations/github/sync")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(
                        handling -> handling.authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
                                .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
