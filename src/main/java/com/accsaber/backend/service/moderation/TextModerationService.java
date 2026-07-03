package com.accsaber.backend.service.moderation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TextModerationService {

    private static final Logger log = LoggerFactory.getLogger(TextModerationService.class);
    private static final int MIN_TERM_LENGTH = 3;

    private static final Map<Character, Character> SUBSTITUTIONS = Map.ofEntries(
            Map.entry('0', 'o'), Map.entry('1', 'i'), Map.entry('3', 'e'), Map.entry('4', 'a'),
            Map.entry('5', 's'), Map.entry('7', 't'), Map.entry('8', 'b'), Map.entry('9', 'g'),
            Map.entry('@', 'a'), Map.entry('$', 's'), Map.entry('!', 'i'), Map.entry('|', 'i'),
            Map.entry('+', 't'),
            Map.entry('а', 'a'), Map.entry('е', 'e'), Map.entry('о', 'o'),
            Map.entry('с', 'c'), Map.entry('р', 'p'), Map.entry('у', 'y'),
            Map.entry('х', 'x'), Map.entry('к', 'k'), Map.entry('м', 'm'),
            Map.entry('т', 't'), Map.entry('в', 'b'), Map.entry('н', 'h'),
            Map.entry('і', 'i'), Map.entry('ј', 'j'), Map.entry('ѕ', 's'),
            Map.entry('ο', 'o'), Map.entry('α', 'a'), Map.entry('ε', 'e'),
            Map.entry('ρ', 'p'), Map.entry('ι', 'i'), Map.entry('κ', 'k'),
            Map.entry('τ', 't'), Map.entry('χ', 'x'), Map.entry('ν', 'v'),
            Map.entry('β', 'b'));

    private final Set<String> blockedTerms;

    public TextModerationService() {
        this.blockedTerms = loadTerms("moderation/blocked-terms.txt");
        log.info("Text moderation loaded {} blocked terms", blockedTerms.size());
    }

    public boolean isClean(String text) {
        if (text == null || text.isBlank() || blockedTerms.isEmpty()) {
            return true;
        }
        StringBuilder singleLetterRun = new StringBuilder();
        for (String token : normalize(text).split("[^a-z]+")) {
            if (isBlockedToken(token)) {
                return false;
            }
            if (token.length() == 1) {
                singleLetterRun.append(token);
            } else {
                if (containsBlockedSlur(singleLetterRun.toString())) {
                    return false;
                }
                singleLetterRun.setLength(0);
            }
        }
        return !containsBlockedSlur(singleLetterRun.toString());
    }

    private boolean isBlockedToken(String token) {
        if (token.length() < MIN_TERM_LENGTH) {
            return false;
        }
        return blockedTerms.contains(collapse(token));
    }

    private boolean containsBlockedSlur(String run) {
        if (run.length() < MIN_TERM_LENGTH) {
            return false;
        }
        String collapsed = collapse(run);
        for (String term : blockedTerms) {
            if (collapsed.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String collapse(String token) {
        return token.replaceAll("(.)\\1{2,}", "$1");
    }

    private static String normalize(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            int type = Character.getType(c);
            if (type == Character.NON_SPACING_MARK || type == Character.FORMAT || type == Character.CONTROL) {
                continue;
            }
            Character mapped = SUBSTITUTIONS.get(c);
            sb.append(mapped != null ? mapped : c);
        }
        return sb.toString();
    }

    private static Set<String> loadTerms(String resource) {
        Set<String> terms = new HashSet<>();
        try (InputStream in = TextModerationService.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.error("Moderation list {} not found on classpath", resource);
                return terms;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String term = line.trim().toLowerCase(Locale.ROOT);
                    if (!term.isEmpty() && !term.startsWith("#")) {
                        terms.add(term);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to load moderation list {}: {}", resource, e.getMessage());
        }
        return terms;
    }
}
