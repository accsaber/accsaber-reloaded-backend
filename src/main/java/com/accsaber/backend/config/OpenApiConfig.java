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
                        Welcome to the AccSaber API. This is what powers the site, the Discord bot \
                        and the in game plugin, and you are very welcome to build things on top of it too.

                        A few things worth knowing before you start.

                        Most of the read endpoints are open and need nothing at all. For the \
                        ones that do want a token, players get theirs by signing in through Steam, Discord or \
                        BeatLeader under Auth, and staff get a separate one from the staff login. Both are \
                        bearer tokens but they are not interchangeable, so have a look at what an endpoint \
                        expects before you send one.

                        Anything returning a list takes page and size, and page starts at \
                        zero. Size defaults to 20 and will not go above 100. Most list endpoints also accept \
                        sort, written as sort=field,direction.

                        **Rate limit:** You get 400 requests every 60 seconds.

                        Every error comes back in the same shape, with an HTTP \
                        status, a short code you can branch on, a message meant for humans, and a \
                        correlationId. If you ever need to ask us about a request that failed, send the \
                        correlationId along with it, because that is how we find it in the logs.
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
                                .addSecuritySchemes(PLAYER_TOKEN, new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("""
                                                                The token a player gets after signing in through Steam, Discord or \
                                                                BeatLeader, or through the in game route the plugin uses. Send it as \
                                                                Authorization: Bearer and it will be read as that player. If the \
                                                                player also happens to be staff, their staff roles come through on \
                                                                this token as well, though only on the subdomain those roles belong \
                                                                to."""))
                                .addSecuritySchemes(STAFF_TOKEN, new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("""
                                                                The token you get back from the staff login. It is separate from a \
                                                                player token and carries the ranking or admin role instead. Send it \
                                                                the same way, as Authorization: Bearer."""));

                ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(components::addSchemas);
                return components;
        }

        private List<Tag> tags() {
                return List.of(
                                new Tag().name("Auth")
                                                .description("""
                                                                How you get a token in the first place. Players sign in through \
                                                                Steam, Discord or BeatLeader, and the game plugin has its own in \
                                                                game route. Once you have one, you pass it as an Authorization \
                                                                header on anything that asks for it."""),
                                new Tag().name("Players")
                                                .description("""
                                                                Everything about a single player. Profiles, name history, per \
                                                                category statistics and how they have shifted over time, skill \
                                                                breakdowns, followers and rivals, notifications, settings and \
                                                                profile customisation."""),
                                new Tag().name("Scores")
                                                .description("""
                                                                Submitting scores, and the practice score board. Reading scores \
                                                                mostly happens under Players or Maps, so this is the writing side \
                                                                plus practice runs."""),
                                new Tag().name("Maps")
                                                .description("""
                                                                Ranked maps and the difficulties inside them. Look one up by id, \
                                                                song hash or BeatSaver code, read its complexity history, and pull \
                                                                the leaderboard for a single difficulty. Release batches sit here \
                                                                too, since a batch is really just a set of difficulties that went \
                                                                ranked together."""),
                                new Tag().name("Leaderboards")
                                                .description("""
                                                                Global and per country rankings for each category, plus the XP \
                                                                board. These are cached for a few minutes, so a score will not \
                                                                always show up the instant it lands."""),
                                new Tag().name("Campaigns")
                                                .description("""
                                                                Campaigns are map progressions that players work through node by \
                                                                node. Anyone can build one and publish it, and the ones that get \
                                                                curated are the ones that hand out XP and items. Browsing them, \
                                                                starting one, following progress, chat and per campaign \
                                                                leaderboards all live here."""),
                                new Tag().name("Clans")
                                                .description("""
                                                                Clans are groups of players who level up together, run                                                                 missions as a team and go to war with other clans over                                                                 Standing. Founding one, joining, ranks, invites and the                                                                 audit log all live here."""),
                                new Tag().name("Milestones")
                                                .description("""
                                                                Milestones and achievements, the sets and groups they belong to, \
                                                                who has finished what, and the XP thresholds sitting behind player \
                                                                levels."""),
                                new Tag().name("Missions and Events")
                                                .description("""
                                                                Daily and weekly missions that get calibrated to how good a player \
                                                                actually is, and the seasonal events that bring their own mission \
                                                                pools with them. Dailies roll over at 4AM server time and weeklies \
                                                                go on Monday."""),
                                new Tag().name("Items and Market")
                                                .description("""
                                                                Cosmetic items, the crates you open to get them, unusual effect \
                                                                variants, straight trades between players, and the market where \
                                                                listings and auctions happen."""),
                                new Tag().name("Playlists")
                                                .description("""
                                                                Downloadable Beat Saber playlist files. Each one carries a syncURL \
                                                                so mod managers can refresh it later on, which is also why these \
                                                                use path segments instead of query parameters. Standalone Beat \
                                                                Saber will not follow a sync URL with a query string on it, so \
                                                                please treat these paths as fixed."""),
                                new Tag().name("Site Statistics")
                                                .description("""
                                                                Site wide numbers. The leaderboard family covers things like \
                                                                streaks, retries and collection completion, and the chart family \
                                                                gives you time series and distributions if you want to graph \
                                                                something."""),
                                new Tag().name("Platform")
                                                .description("""
                                                                The pieces holding everything else up. Health checks, categories, \
                                                                scoring curves, modifiers, CDN limits, news, the metadata behind \
                                                                link previews, and the Ko-fi webhook receiver."""),

                                new Tag().name("Ranking")
                                                .description("""
                                                                Everything the ranking team works with. Reading the queue with the \
                                                                staff only fields attached, importing new difficulties, voting, \
                                                                moving things between statuses, reweights and unranks, batches, and \
                                                                ranking news posts."""),
                                new Tag().name("Staff Accounts")
                                                .description("""
                                                                Staff sign in and account management. Staff authenticate separately \
                                                                from players, so a staff token and a player token are not the same \
                                                                thing even when the same person holds both."""),
                                new Tag().name("Admin - Campaigns")
                                                .description("""
                                                                Curating, publishing and moderating campaigns, and editing them \
                                                                while they are already live."""),
                                new Tag().name("Admin - Clans")
                                                .description("""
                                                                Tuning the clan level table, running clan seasons and the war                                                                 reward pool, and stepping in when a clan breaks the rules."""),
                                new Tag().name("Admin - Items and Crates")
                                                .description("""
                                                                The item catalog itself. Types and items, what is inside a crate \
                                                                and how likely each thing is, unusual effects, and awarding or \
                                                                taking back an item. Staff with the creative role can read the \
                                                                catalog here as well, they just cannot change any of it."""),
                                new Tag().name("Admin - Milestones and Missions")
                                                .description("""
                                                                Writing milestones and missions. Sets, groups, prerequisites, \
                                                                mission templates, and the backfills that apply a new one to \
                                                                players who already qualified."""),
                                new Tag().name("Admin - Events and News")
                                                .description("Seasonal events and news posts, artwork included."),
                                new Tag().name("Admin - Players")
                                                .description("""
                                                                Looking after player accounts. Bans, country overrides, profile \
                                                                refreshes, spotting and merging duplicate accounts, supporter \
                                                                grants and Discord links."""),
                                new Tag().name("Admin - Operations")
                                                .description("""
                                                                The heavy jobs and the plumbing. Score backfills, AP and XP \
                                                                recalculations, CDN backfills, control over the WebSocket feeds, \
                                                                and broadcast notifications. Most of these go off in the background \
                                                                and hand you a 202 straight away."""));
        }
}
