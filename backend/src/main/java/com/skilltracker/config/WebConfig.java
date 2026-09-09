package com.skilltracker.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves uploaded attachments from the configured upload directory under {@code /uploads}. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    public WebConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        createUploadDirectory();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(storageProperties.uploadPath().toUri().toString());
    }

    private void createUploadDirectory() {
        Path uploadPath = storageProperties.uploadPath();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot create upload directory " + uploadPath, failure);
        }
    }
}
