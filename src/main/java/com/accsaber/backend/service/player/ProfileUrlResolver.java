package com.accsaber.backend.service.player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProfileUrlResolver {

        private static final Pattern BEATLEADER_PATTERN = Pattern.compile(
                        "(?:https?://)?(?:www\\.)?beatleader\\.(?:com|xyz)/u/([\\w-]+)");
        private static final Pattern SCORESABER_PATTERN = Pattern.compile(
                        "(?:https?://)?(?:www\\.)?scoresaber\\.com/u/(\\d+)");

        private final BeatLeaderClient beatLeaderClient;
        private final ScoreSaberClient scoreSaberClient;

        public String resolve(String profileUrl) {
                Matcher blMatcher = BEATLEADER_PATTERN.matcher(profileUrl);
                if (blMatcher.find()) {
                        String identifier = blMatcher.group(1);
                        return beatLeaderClient.getPlayer(identifier)
                                        .orElseThrow(() -> new ResourceNotFoundException("BeatLeader player",
                                                        identifier))
                                        .getId();
                }

                Matcher ssMatcher = SCORESABER_PATTERN.matcher(profileUrl);
                if (ssMatcher.find()) {
                        String id = ssMatcher.group(1);
                        scoreSaberClient.getPlayer(id)
                                        .orElseThrow(() -> new ResourceNotFoundException("ScoreSaber player", id));
                        return id;
                }

                if (profileUrl.matches("\\d+")) {
                        return beatLeaderClient.getPlayer(profileUrl)
                                        .orElseThrow(() -> new ResourceNotFoundException("Player", profileUrl))
                                        .getId();
                }

                throw new ValidationException(
                                "Invalid profile URL. Use a BeatLeader or ScoreSaber profile link, or a numeric ID.");
        }
}
