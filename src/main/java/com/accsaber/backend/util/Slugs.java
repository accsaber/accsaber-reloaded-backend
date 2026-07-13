package com.accsaber.backend.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Slugs {

    private static final Pattern NON_SLUG_RUNS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    private Slugs() {
    }

    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String lowered = NON_SLUG_RUNS.matcher(input.toLowerCase(Locale.ROOT)).replaceAll("-");
        return EDGE_DASHES.matcher(lowered).replaceAll("");
    }
}
