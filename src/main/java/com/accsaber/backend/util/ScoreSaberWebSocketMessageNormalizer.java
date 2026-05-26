package com.accsaber.backend.util;

import java.util.ArrayList;
import java.util.List;

import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberWebSocketMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ScoreSaberWebSocketMessageNormalizer {

    private ScoreSaberWebSocketMessageNormalizer() {
    }

    public static ScoreSaberWebSocketMessage normalize(String rawJson, ObjectMapper mapper) throws Exception {
        JsonNode root = mapper.readTree(rawJson);
        if (isV1Envelope(root)) {
            return translateV1(root);
        }
        return mapper.treeToValue(root, ScoreSaberWebSocketMessage.class);
    }

    private static boolean isV1Envelope(JsonNode root) {
        return root.has("commandName") && root.has("commandData");
    }

    private static ScoreSaberWebSocketMessage translateV1(JsonNode root) {
        JsonNode data = root.path("commandData");
        ScoreSaberWebSocketMessage envelope = new ScoreSaberWebSocketMessage();
        envelope.setScore(translateScore(data.path("score")));
        envelope.setLeaderboard(translateLeaderboard(data.path("leaderboard")));
        envelope.setScoreStats(null);
        return envelope;
    }

    private static ScoreSaberScoreResponse translateScore(JsonNode n) {
        if (n.isMissingNode() || n.isNull()) {
            return null;
        }
        ScoreSaberScoreResponse s = new ScoreSaberScoreResponse();
        s.setId(longOrNull(n, "id"));
        s.setRank(intOrNull(n, "rank"));
        s.setUnmodifiedScore(intOrNull(n, "baseScore"));
        s.setModifiedScore(intOrNull(n, "modifiedScore"));
        s.setMaxCombo(intOrNull(n, "maxCombo"));
        s.setBadCuts(intOrNull(n, "badCuts"));
        s.setMissedNotes(intOrNull(n, "missedNotes"));
        s.setCreatedAt(stringOrNull(n, "timeSet"));
        s.setMods(parseModifiersCsv(stringOrNull(n, "modifiers")));

        String deviceHmd = stringOrNull(n, "deviceHmd");
        if (deviceHmd != null) {
            ScoreSaberScoreResponse.Device device = new ScoreSaberScoreResponse.Device();
            device.setHmd(deviceHmd);
            s.setDevice(device);
        }

        JsonNode playerInfo = n.path("leaderboardPlayerInfo");
        if (!playerInfo.isMissingNode() && !playerInfo.isNull()) {
            ScoreSaberScoreResponse.Player player = new ScoreSaberScoreResponse.Player();
            player.setId(stringOrNull(playerInfo, "id"));
            s.setPlayer(player);
        }
        return s;
    }

    private static ScoreSaberLeaderboardResponse translateLeaderboard(JsonNode n) {
        if (n.isMissingNode() || n.isNull()) {
            return null;
        }
        ScoreSaberLeaderboardResponse lb = new ScoreSaberLeaderboardResponse();
        lb.setId(longOrNull(n, "id"));
        lb.setMaxScore(intOrNull(n, "maxScore"));

        ScoreSaberLeaderboardResponse.Map map = new ScoreSaberLeaderboardResponse.Map();
        map.setHash(stringOrNull(n, "songHash"));
        map.setSongName(stringOrNull(n, "songName"));
        map.setSongAuthorName(stringOrNull(n, "songAuthorName"));
        map.setLevelAuthorName(stringOrNull(n, "levelAuthorName"));
        lb.setMap(map);
        return lb;
    }

    private static List<String> parseModifiersCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String tok : csv.split(",")) {
            String t = tok.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static String stringOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        return v.asText();
    }

    private static Integer intOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull() || !v.canConvertToInt()) {
            return null;
        }
        return v.asInt();
    }

    private static Long longOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull() || !v.canConvertToLong()) {
            return null;
        }
        return v.asLong();
    }
}
