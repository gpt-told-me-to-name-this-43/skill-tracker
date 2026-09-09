package com.skilltracker.service;

import com.skilltracker.config.StorageProperties;
import com.skilltracker.exception.BadRequestException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes uploaded attachments below the configured upload directory and never outside it. */
@Component
public class AttachmentStorage {

    public static final String PUBLIC_PREFIX = "/uploads/task-attachments/";
    private static final String FALLBACK_NAME = "attachment";

    private final StorageProperties storageProperties;

    public AttachmentStorage(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    /** The client-supplied name reduced to its final path segment, as shown in the UI. */
    public String displayName(String filename) {
        if (filename == null || filename.isBlank()) {
            return FALLBACK_NAME;
        }
        Path name = Path.of(filename.replace('\\', '/')).getFileName();
        return name == null || name.toString().isBlank() ? FALLBACK_NAME : name.toString();
    }

    /** Stores the bytes under a generated name and returns the public {@code /uploads/...} path. */
    public String store(String displayName, byte[] content) {
        String storedName = UUID.randomUUID().toString().replace("-", "") + "-" + sanitize(displayName);
        Path directory = storageProperties.taskAttachmentsPath();
        Path target = directory.resolve(storedName).normalize();

        if (!target.startsWith(directory)) {
            throw new BadRequestException("Invalid attachment file name");
        }

        try {
            Files.createDirectories(directory);
            Files.write(target, content);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }

        return PUBLIC_PREFIX + storedName;
    }

    private String sanitize(String displayName) {
        StringBuilder safe = new StringBuilder(displayName.length());
        for (char character : displayName.toCharArray()) {
            safe.append(isSafe(character) ? character : '-');
        }

        String trimmed = trimDotsAndDashes(safe.toString());
        return trimmed.isEmpty() ? FALLBACK_NAME : trimmed;
    }

    private boolean isSafe(char character) {
        return Character.isLetterOrDigit(character) || character == '.' || character == '-' || character == '_';
    }

    private String trimDotsAndDashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == '.' || value.charAt(start) == '-')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) == '.' || value.charAt(end - 1) == '-')) {
            end--;
        }
        return value.substring(start, end);
    }
}
