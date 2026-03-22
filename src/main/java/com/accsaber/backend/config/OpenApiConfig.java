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
                                                .title("AccSaber Reloaded API")
                                                .description("""
                                                                REST API for the AccSaber Reloaded platform - an accuracy-based \
                                                                leaderboard system for Beat Saber.
                                                                """)
                                                .version("ALPHA-1.0.0")
                                                .contact(new Contact()
                                                                .name("AccSaber Reloaded")
                                                                .url(baseUrl)))
                                .tags(List.of(
                                                new Tag().name("Health")
                                                                .description("Service health and status checks"),
                                                new Tag().name("Categories")
                                                                .description("Scoring categories (True Acc, Standard Acc, Tech Acc, etc.) and their associated curves"),
                                                new Tag().name("Modifiers")
                                                                .description("Score modifiers (NF, DA, etc.) and their multipliers"),
                                                new Tag().name("Maps")
                                                                .description("Ranked maps, difficulties, complexity history, and per-difficulty leaderboards"),
                                                new Tag().name("Batches")
                                                                .description("Ranking batches - curated groups of qualified maps released together (public reads)"),
                                                new Tag().name("Players")
                                                                .description("Player profiles, score history, milestones, level, and campaign progress"),
                                                new Tag().name("Leaderboards")
                                                                .description("Global and country leaderboard rankings by category"),
                                                new Tag().name("Milestones")
                                                                .description("Milestone sets, individual milestones/achievements, and level thresholds"),
                                                new Tag().name("Campaigns")
                                                                .description("Campaign progressions with curated map sequences and XP rewards"),
                                                new Tag().name("Playlists")
                                                                .description("Downloadable Beat Saber playlist files for each ranked category, with syncURL for auto-updates"),
                                                new Tag().name("Curves")
                                                                .description("Scoring curves - point-lookup and formula curves used for AP and weight calculations"),
                                                new Tag().name("Discord Links")
                                                                .description("Discord-to-player account linking and lookup"),

                                                new Tag().name("Ranking - Map Import")
                                                                .description("Import new map difficulties into the ranking queue (Ranking role)"),
                                                new Tag().name("Ranking - Map Votes")
                                                                .description("Cast, list, and manage votes on map difficulties (Ranking / RANKING_HEAD roles)"),
                                                new Tag().name("Ranking - Map Difficulty Management")
                                                                .description("Status transitions, complexity updates, reweights, unranks, and criteria webhooks (RANKING_HEAD role)"),
                                                new Tag().name("Ranking - Batches")
                                                                .description("Create, manage, and release ranking batches (RANKING_HEAD role)"),

                                                new Tag().name("Admin Import")
                                                                .description("Bulk imports, score backfills, and player profile refreshes (Admin role)"),
                                                new Tag().name("Admin Recalculation")
                                                                .description("Trigger recalculations for leaderboards, difficulties, AP/XP curves, and weight curves (Admin role)"),
                                                new Tag().name("Admin Milestones")
                                                                .description("Create, deactivate, backfill milestones and refresh completion stats (Admin role)"),
                                                new Tag().name("Admin Campaigns")
                                                                .description("Create, update, deactivate campaigns and manage campaign maps (Admin role)"),
                                                new Tag().name("Admin User Duplicates")
                                                                .description("Detect, link, and merge duplicate user accounts across platforms (Admin role)"),
                                                new Tag().name("Admin Curves")
                                                                .description("Create and update scoring curves (Admin role)"),
                                                new Tag().name("Admin WebSocket")
                                                                .description("Monitor and reconnect BeatLeader/ScoreSaber WebSocket feeds (Admin role)"),

                                                new Tag().name("Staff Auth")
                                                                .description("Staff login, token refresh, and logout"),
                                                new Tag().name("Staff Users")
                                                                .description("Staff account management - profiles, roles, status, and OAuth links (Admin role)")))
                                .addSecurityItem(new SecurityRequirement().addList("Bearer Token"))
                                .schemaRequirement("Bearer Token", new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                                "JWT authentication for protected endpoints. "
                                                                                + "Obtain a token via POST /v1/staff/auth/login, "
                                                                                + "then pass it as: Authorization: Bearer <token>"));
        }
}
