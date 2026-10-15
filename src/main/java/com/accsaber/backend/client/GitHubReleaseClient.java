package com.accsaber.backend.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GitHubReleaseClient {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    public record GitHubAsset(String name, long size, String downloadUrl) {
    }

    public record GitHubRelease(String tag, String name, Instant publishedAt, boolean prerelease,
            List<GitHubAsset> assets) {
    }

    private record RawAsset(String name, long size, @JsonProperty("browser_download_url") String downloadUrl) {
    }

    private record RawRelease(@JsonProperty("tag_name") String tag, String name,
            @JsonProperty("published_at") String publishedAt, boolean prerelease, boolean draft,
            List<RawAsset> assets) {
    }

    private final WebClient webClient;

    @Value("${accsaber.quest.github-repo:accsaber/accsaber-qlite-plugin}")
    private String repo;

    @Value("${accsaber.quest.github-token:}")
    private String token;

    public GitHubReleaseClient(@Qualifier("gitHubWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public List<GitHubRelease> listReleases() {
        try {
            RawRelease[] body = authorized(webClient.get()
                    .uri("/repos/" + repo + "/releases?per_page=20")
                    .header("Accept", "application/vnd.github+json"))
                    .retrieve()
                    .bodyToMono(RawRelease[].class)
                    .block(HTTP_TIMEOUT);
            if (body == null) {
                return List.of();
            }
            return Arrays.stream(body)
                    .filter(release -> !release.draft())
                    .map(GitHubReleaseClient::toRelease)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to list GitHub releases for {}: {}", repo, e.getMessage());
            return List.of();
        }
    }

    public Optional<byte[]> downloadAsset(String downloadUrl) {
        try {
            return Optional.ofNullable(authorized(webClient.get()
                    .uri(java.net.URI.create(downloadUrl))
                    .header("Accept", "application/octet-stream"))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(HTTP_TIMEOUT));
        } catch (Exception e) {
            log.error("Failed to download GitHub asset {}: {}", downloadUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private WebClient.RequestHeadersSpec<?> authorized(WebClient.RequestHeadersSpec<?> spec) {
        return token == null || token.isBlank() ? spec : spec.header("Authorization", "Bearer " + token);
    }

    private static GitHubRelease toRelease(RawRelease release) {
        List<GitHubAsset> assets = release.assets() == null ? List.of()
                : release.assets().stream()
                        .map(asset -> new GitHubAsset(orEmpty(asset.name()), asset.size(),
                                orEmpty(asset.downloadUrl())))
                        .toList();
        return new GitHubRelease(orEmpty(release.tag()), orEmpty(release.name()),
                parseInstant(release.publishedAt()), release.prerelease(), assets);
    }

    private static String orEmpty(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
