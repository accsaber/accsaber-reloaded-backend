package com.accsaber.backend.config;

import java.util.ArrayList;
import java.util.List;

import com.accsaber.backend.security.BannedUserWriteFilter;
import com.accsaber.backend.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("RANKING_HEAD")
                .role("RANKING_HEAD").implies("RANKING")
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${accsaber.domains}") List<String> domains) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> patterns = new ArrayList<>();
        for (String domain : domains) {
            patterns.add("https://" + domain);
            patterns.add("https://*." + domain);
        }
        patterns.add("http://localhost:*");
        patterns.add("http://*.localhost:*");
        patterns.add("http://127.0.0.1:*");
        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "X-Rate-Limit-Remaining"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            BannedUserWriteFilter bannedUserWriteFilter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/v1/health/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v1/swagger-ui/**", "/v1/docs/**", "/v1/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/staff/auth/login", "/v1/staff/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/auth/*/start", "/v1/auth/*/callback").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/auth/refresh", "/v1/auth/logout", "/v1/auth/ingame").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/curves/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/categories/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/modifiers/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/users/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/maps/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/batches/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/leaderboards/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/statistics/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/milestones/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/levels/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/campaigns/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/events/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/crates/*/contents", "/v1/crates/*/modifiers", "/v1/crates/*/unusual-effects").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/item-types", "/v1/item-modifiers", "/v1/unusual-effects", "/v1/items", "/v1/items/*", "/v1/items/*/holders").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/market/listings", "/v1/market/listings/*", "/v1/market/listings/*/bids").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/news/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/playlists/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/songsuggest", "/v1/songsuggest/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/discord/links/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/staff/users-public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/calculate/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/og/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/cdn/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/cdn/limits").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/webhooks/kofi").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/supporters/credits").permitAll()
                .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/v1/ranking/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(bannedUserWriteFilter, JwtAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                        exceptionResolver.resolveException(request, response, null, e))
                .accessDeniedHandler((request, response, e) ->
                        exceptionResolver.resolveException(request, response, null, e))
            );

        return http.build();
    }
}
