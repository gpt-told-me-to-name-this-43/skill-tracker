package com.skilltracker.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Mirrors the UPLOAD_DIR environment contract. */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String uploadDir) {

    public Path uploadPath() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path taskAttachmentsPath() {
        return uploadPath().resolve("task-attachments");
    }
}
