package com.accsaber.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

class FrozenApiContractTest {

    private static final String CONTROLLER_PACKAGE = "com.accsaber.backend.controller";

    private static final List<String> SHIPPED_PLUGIN_ROUTES = List.of(
            "POST /v1/auth/ingame",
            "POST /v1/auth/logout",
            "POST /v1/submit",
            "GET /v1/health/ping",
            "GET /v1/modifiers",
            "GET /v1/curves/{id}",
            "GET /v1/news",
            "GET /v1/batches",
            "GET /v1/milestones/{id}",
            "GET /v1/maps/{mapId}",
            "GET /v1/maps/hash/{songHash}",
            "GET /v1/maps/difficulties",
            "GET /v1/maps/difficulties/all",
            "GET /v1/maps/difficulties/{difficultyId}",
            "GET /v1/maps/difficulties/{difficultyId}/scores",
            "GET /v1/leaderboards/{category}",
            "GET /v1/users/{userId}",
            "GET /v1/users/{userId}/scores",
            "GET /v1/users/{userId}/scores/all",
            "GET /v1/users/{userId}/scores/by-hash/{songHash}",
            "GET /v1/users/{userId}/stats-diff",
            "GET /v1/users/{userId}/milestones",
            "GET /v1/users/{userId}/milestones/completed",
            "GET /v1/users/{userId}/milestones/uncompleted",
            "GET /v1/users/{userId}/items/equipped",
            "GET /v1/users/{userId}/maps-above-ap",
            "GET /v1/users/{userId}/relations",
            "GET /v1/users/me/relations",
            "POST /v1/users/me/relations",
            "DELETE /v1/users/me/relations/{relationId}",
            "GET /v1/users/me/settings/{group}",
            "GET /v1/users/me/missions",
            "GET /v1/campaigns",
            "GET /v1/campaigns/tags",
            "GET /v1/campaigns/me",
            "GET /v1/campaigns/me/progress",
            "GET /v1/campaigns/{campaignId}/me/progress",
            "GET /v1/campaigns/{campaignId}",
            "POST /v1/campaigns/{campaignId}/start",
            "GET /v1/events",
            "GET /v1/events/{idOrSlug}",
            "GET /v1/events/{idOrSlug}/me",
            "GET /v1/events/{idOrSlug}/missions/me",
            "POST /v1/events/{idOrSlug}/begin",
            "GET /v1/playlists/{category}",
            "GET /v1/playlists/missing/{userId}/{category}",
            "GET /v1/playlists/snipe/{sniperId}/{targetId}/{size}/{category}");

    private static final List<String> BAKED_PLAYLIST_SYNC_ROUTES = List.of(
            "GET /v1/playlists/{category}",
            "GET /v1/playlists/unranked/{category}",
            "GET /v1/playlists/missing/{userId}/{category}",
            "GET /v1/playlists/batch/{batchId}",
            "GET /v1/playlists/campaign/{campaignId}",
            "GET /v1/playlists/snipe/{sniperId}/{targetId}/{size}",
            "GET /v1/playlists/snipe/{sniperId}/{targetId}/{size}/{category}");

    @Test
    void everyRouteTheShippedPluginCallsStillExists() {
        assertThat(declaredRoutes()).containsAll(SHIPPED_PLUGIN_ROUTES);
    }

    @Test
    void everyPlaylistRouteBakedIntoDownloadedFilesStillExists() {
        assertThat(declaredRoutes()).containsAll(BAKED_PLAYLIST_SYNC_ROUTES);
    }

    private Set<String> declaredRoutes() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<String> routes = new TreeSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> controller = ClassUtils.resolveClassName(definition.getBeanClassName(), null);
            collectRoutes(controller, routes);
        }
        return routes;
    }

    private void collectRoutes(Class<?> controller, Set<String> routes) {
        RequestMapping typeMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        Set<String> basePaths = pathsOf(typeMapping);

        for (var method : controller.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            for (RequestMethod verb : mapping.method()) {
                for (String base : basePaths) {
                    for (String suffix : pathsOf(mapping)) {
                        routes.add(verb.name() + " " + join(base, suffix));
                    }
                }
            }
        }
    }

    private Set<String> pathsOf(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0) {
            return new LinkedHashSet<>(List.of(""));
        }
        return new LinkedHashSet<>(Arrays.asList(mapping.path()));
    }

    private String join(String base, String suffix) {
        String combined = base + suffix;
        return combined.isEmpty() ? "/" : combined;
    }
}
