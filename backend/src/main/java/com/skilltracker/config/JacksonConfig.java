package com.skilltracker.config;

import com.skilltracker.json.Patch;
import com.skilltracker.json.PatchDeserializer;
import com.skilltracker.json.UtcLocalDateTimeDeserializer;
import java.time.LocalDateTime;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.module.SimpleModule;

/**
 * Pins the wire format to the one the React client already consumes: snake_case property names and
 * naive ISO-8601 timestamps.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer skillTrackerJsonCustomizer() {
        SimpleModule module = new SimpleModule("skill-tracker");
        module.addDeserializer(Patch.class, new PatchDeserializer());
        module.addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());

        return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .addModule(module);
    }
}
