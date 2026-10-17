package com.accsaber.backend.config;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.accsaber.backend.model.dto.response.ErrorResponse;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
public class OpenApiConfig {

        public static final String PLAYER_TOKEN = "Player Token";
        public static final String STAFF_TOKEN = "Staff Token";

        private static final String INTRO = """
                        The API behind the AccSaber site, Discord bot and in game plugin.

                        Lists take `page` (from 0), `size` (default 20, max 600) and `sort=field,direction`.                         The limit is 400 requests per 60 seconds. Errors carry a `code` to branch on and a                         `correlationId` to quote when you report one.
                        """;

        @Bean
        public OpenAPI openAPI(@Value("${accsaber.domains}") List<String> domains,
                        ObjectProvider<BuildProperties> buildProperties) {
                String baseUrl = isProduction()
                                ? "https://" + domains.get(0)
                                : "https://localhost:8080";

                String version = buildProperties.getIfAvailable() != null
                                ? buildProperties.getIfAvailable().getVersion()
                                : "dev";

                return new OpenAPI()
                                .info(new Info()
                                                .title("AccSaber Reloaded API")
                                                .description(INTRO)
                                                .version(version)
                                                .contact(new Contact()
                                                                .name("AccSaber Reloaded")
                                                                .url(baseUrl)))
                                .tags(tags())
                                .components(components());
        }

        private boolean isProduction() {
                String profile = System.getenv("SPRING_PROFILES_ACTIVE");
                return profile != null && profile.equalsIgnoreCase("prod");
        }

        private Components components() {
                Components components = new Components()
                                .addSecuritySchemes(PLAYER_TOKEN, bearer("From a Steam, Discord, BeatLeader or plugin sign in."))
                                .addSecuritySchemes(STAFF_TOKEN, bearer("From the staff login. Not interchangeable with a player token."));

                ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(components::addSchemas);
                return components;
        }

        private SecurityScheme bearer(String description) {
                return new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(description);
        }

        private List<Tag> tags() {
                return List.of(
                                tag("Auth", "Player sign in through Steam, Discord, BeatLeader or the plugin."),
                                tag("Players", "Profiles, statistics, skills, relations, notifications and settings."),
                                tag("Scores", "Practice scores."),
                                tag("Maps", "Ranked maps, difficulties, their leaderboards and release batches."),
                                tag("Leaderboards", "Category and XP rankings. Cached for up to 10 minutes."),
                                tag("Campaigns", "Player built map progressions. Curated ones award XP and items."),
                                tag("Clans", "Membership, levels, missions, alliances, rivals and wars."),
                                tag("Milestones", "Milestones, achievements and level thresholds."),
                                tag("Missions and Events", "Dailies reset at 4AM server time, weeklies on Monday."),
                                tag("Items and Market", "Items, crates, trades and the market."),
                                tag("Playlists", "Paths are fixed. Standalone Beat Saber cannot sync a URL with a query string."),
                                tag("Site Statistics", "Site wide boards and charts."),
                                tag("Quest", "Quest mod releases and downloads."),
                                tag("Platform", "Health, categories, curves, modifiers, news, link previews and the Ko-fi webhook."),
                                tag("Ranking", "Queue, imports, votes, status changes, reweights and batches."),
                                tag("Ranking - Complexity", "The complexity script panel."),
                                tag("Staff Accounts", "Staff login and account management."),
                                tag("Admin - Campaigns", "Curation and live campaign edits."),
                                tag("Admin - Clans", "Level table, seasons, war rewards and moderation."),
                                tag("Admin - Items and Crates", "The item catalog, crate contents and unusual effects."),
                                tag("Admin - Milestones and Missions", "Milestones, mission templates and their backfills."),
                                tag("Admin - Events and News", "Events and news posts."),
                                tag("Admin - Players", "Bans, overrides, duplicates, supporters and Discord links."),
                                tag("Admin - Operations", "Backfills, recalculations, WebSocket feeds and broadcasts. Jobs answer 202."));
        }

        private static Tag tag(String name, String description) {
                return new Tag().name(name).description(description);
        }
}
