package com.accsaber.backend.service.map;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComplexityDatasetService {

    private static final int FETCH_SIZE = 2000;

    private static final String SCORES_SQL = """
            SELECT s.id AS score_id, s.user_id, s.map_difficulty_id, c.code AS category, d.max_score,
                   s.score, s.score_no_mods, s.ap, s.weighted_ap, s.rank, s.rank_when_set,
                   s.misses, s.bad_cuts, s.max_combo, s.streak_115, s.pauses, s.play_count,
                   s.wall_hits, s.bomb_hits, s.hmd, s.time_set, s.created_at,
                   s.active, s.partial, s.reweight_derivative, s.supersedes_reason, s.supersedes_id,
                   s.bl_score_id, s.ss_score_id,
                   (SELECT string_agg(m.code, '|' ORDER BY m.code)
                      FROM score_modifier_links l JOIN modifiers m ON m.id = l.modifier_id
                     WHERE l.score_id = s.id) AS modifiers,
                   u.country, u.banned AS user_banned, u.active AS user_active, u.player_inactive
              FROM scores s
              JOIN map_difficulties d ON d.id = s.map_difficulty_id
              JOIN categories c ON c.id = d.category_id
              JOIN users u ON u.id = s.user_id
             WHERE d.active = true AND d.status = 'ranked'
             ORDER BY s.map_difficulty_id, s.user_id, s.created_at
            """;

    private static final String DIFFICULTIES_SQL = """
            SELECT d.id AS map_difficulty_id, d.map_id, m.song_name, m.song_subname, m.song_author, m.map_author,
                   m.song_hash, m.beatsaver_code, d.bl_leaderboard_id, d.ss_leaderboard_id,
                   d.difficulty, d.characteristic, c.code AS category, d.status, cx.complexity,
                   d.ranked_at, d.batch_id, d.max_score, d.notes, d.bombs, d.walls, d.duration, d.bpm,
                   (SELECT count(*) FROM scores s WHERE s.map_difficulty_id = d.id AND s.active = true) AS active_scores
              FROM map_difficulties d
              JOIN maps m ON m.id = d.map_id
              LEFT JOIN categories c ON c.id = d.category_id
              LEFT JOIN map_difficulty_complexities cx ON cx.map_difficulty_id = d.id AND cx.active = true
             WHERE d.active = true AND d.status IN ('ranked', 'queue', 'qualified')
             ORDER BY d.status, d.ranked_at, m.song_name, d.difficulty
            """;

    private static final String COMPLEXITY_HISTORY_SQL = """
            SELECT cx.id AS complexity_id, cx.map_difficulty_id, cx.complexity, cx.supersedes_id,
                   cx.supersedes_reason, cx.supersedes_author, cx.active, cx.created_at
              FROM map_difficulty_complexities cx
              JOIN map_difficulties d ON d.id = cx.map_difficulty_id
             WHERE d.active = true AND d.status = 'ranked'
             ORDER BY cx.map_difficulty_id, cx.created_at
            """;

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public void writeScores(OutputStream out) {
        stream(SCORES_SQL, out);
    }

    @Transactional(readOnly = true)
    public void writeDifficulties(OutputStream out) {
        stream(DIFFICULTIES_SQL, out);
    }

    @Transactional(readOnly = true)
    public void writeComplexityHistory(OutputStream out) {
        stream(COMPLEXITY_HISTORY_SQL, out);
    }

    private void stream(String sql, OutputStream out) {
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 1 << 16);
        jdbcTemplate.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setFetchSize(FETCH_SIZE);
            return statement;
        }, (ResultSet rs) -> {
            try {
                writeCsv(rs, writer);
                writer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        });
    }

    private static void writeCsv(ResultSet rs, Writer writer) throws SQLException, IOException {
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        for (int i = 1; i <= columns; i++) {
            if (i > 1) {
                writer.write(',');
            }
            writer.write(meta.getColumnLabel(i));
        }
        writer.write('\n');
        while (rs.next()) {
            for (int i = 1; i <= columns; i++) {
                if (i > 1) {
                    writer.write(',');
                }
                writer.write(csvField(rs.getObject(i)));
            }
            writer.write('\n');
        }
    }

    static String csvField(Object value) {
        if (value == null) {
            return "";
        }
        String text;
        if (value instanceof Timestamp timestamp) {
            text = timestamp.toInstant().toString();
        } else if (value instanceof OffsetDateTime offset) {
            text = offset.toInstant().toString();
        } else {
            text = value.toString();
        }
        boolean needsQuotes = text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
