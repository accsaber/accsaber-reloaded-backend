package com.accsaber.backend.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SearchText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private SearchText() {
    }

    public static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String search, String... fields) {
        String needle = normalise(search).trim();
        if (needle.isEmpty()) {
            return true;
        }
        for (String field : fields) {
            if (normalise(field).contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
