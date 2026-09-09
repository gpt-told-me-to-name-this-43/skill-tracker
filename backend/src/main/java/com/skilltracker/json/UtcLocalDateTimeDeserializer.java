package com.skilltracker.json;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Accepts both offset-aware and naive ISO-8601 timestamps and normalises them to naive UTC, which
 * is how timestamps are stored in the database.
 *
 * <p>The React client sends {@code Date#toISOString()} (always UTC with a {@code Z} suffix), while
 * the FastAPI backend also accepted naive values and treated them as UTC.
 */
public class UtcLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
        String raw = parser.getString();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String text = raw.trim();
        try {
            return OffsetDateTime.parse(text)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException notOffsetAware) {
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeParseException invalid) {
                throw new InvalidTimestampException(text);
            }
        }
    }

    /** Signals a malformed timestamp so the error layer can answer with a validation response. */
    public static class InvalidTimestampException extends RuntimeException {
        private final String value;

        InvalidTimestampException(String value) {
            super("Input should be a valid datetime");
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
