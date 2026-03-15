package com.accsaber.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI openAPI() {
                String baseUrl = System.getenv("SPRING_PROFILES_ACTIVE") != null &&
                                System.getenv("SPRING_PROFILES_ACTIVE").equalsIgnoreCase("prod")
                                                ? "https://accsaberreloaded.com"
                                                : "https://localhost:8080";

                return new OpenAPI()
                                .info(new Info()
                                                .title("AccSaber API")
                                                .description("""
                                                                REST API for the AccSaber Reloaded platform - a revamped accuracy-based leaderboard \
                                                                system for Beat Saber.

                                                                Accsaber Reloaded further expands on the original Accsaber concept with new QOL features.
                                                                """)
                                                .version("0.2.0")
                                                .contact(new Contact()
                                                                .name("AccSaber Reloaded")
                                                                .url(baseUrl)))
                                .tags(List.of(
                                                new Tag().name("Players")
                                                                .description("Player profiles, statistics, and score history"),
                                                new Tag().name("Leaderboards")
                                                                .description("Global and country leaderboard rankings by category"),
                                                new Tag().name("Scores")
                                                                .description("Score submissions and retrieval"),
                                                new Tag().name("Maps")
                                                                .description("Ranked map information, difficulties, complexity ratings, and statuses"),
                                                new Tag().name("Modifiers")
                                                                .description("Score modifiers and their multipliers"),
                                                new Tag().name("Batches")
                                                                .description("Ranking batches - curated groups of qualified maps released together"),
                                                new Tag().name("Categories")
                                                                .description("Scoring categories and their associated curves"),
                                                new Tag().name("Milestones")
                                                                .description("Milestone sets, milestones, achievements, and player completion"),
                                                new Tag().name("Levels")
                                                                .description("XP totals, level thresholds, and player progression"),
                                                new Tag().name("Campaigns")
                                                                .description("Campaign progressions, milestones, XP rewards, and player completion"),
                                                new Tag().name("Health")
                                                                .description("Service health and status checks"),
                                                new Tag().name("Discord Links")
                                                                .description("Discord-to-player account linking"),
                                                new Tag().name("Admin")
                                                                .description("Administrative operations (authenticated)")))
                                .addSecurityItem(new SecurityRequirement().addList("Bearer Token"))
                                .schemaRequirement("Bearer Token", new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("JWT authentication for protected endpoints"));
        }
}
