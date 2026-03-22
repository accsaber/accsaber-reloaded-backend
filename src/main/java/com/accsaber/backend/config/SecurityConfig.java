package com.accsaber.backend.config;

import com.accsaber.backend.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Qualifier;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/v1/health/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v1/swagger-ui/**", "/v1/docs/**", "/v1/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/staff/auth/login", "/v1/staff/auth/refresh", "/v1/staff/auth/request").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/curves/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/categories/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/modifiers/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/users/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/maps/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/batches/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/leaderboards/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/milestones/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/levels/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/campaigns/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/playlists/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/discord/links/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/discord/links").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/staff/users-public/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/v1/ranking/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                        exceptionResolver.resolveException(request, response, null, e))
                .accessDeniedHandler((request, response, e) ->
                        exceptionResolver.resolveException(request, response, null, e))
            );

        return http.build();
    }
}
