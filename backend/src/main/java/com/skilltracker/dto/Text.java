package com.skilltracker.dto;

import java.net.URI;
import java.util.Locale;

/** Input normalisation shared by the request records. */
public final class Text {

    private Text() {}

    /** Trims and collapses an empty result to null, the way the pydantic validators did. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            String normalised = scheme.toLowerCase(Locale.ROOT);
            return ("http".equals(normalised) || "https".equals(normalised))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
