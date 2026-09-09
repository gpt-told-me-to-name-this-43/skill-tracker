package com.skilltracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documents the API with the same HTTP bearer scheme the previous OpenAPI document declared: the
 * login endpoint returns a token that is pasted into Authorize, not an OAuth2 password flow.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI skillTrackerOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Skill Tracker").version("0.1.0"))
                .components(new Components()
                        .addSecuritySchemes(
                                "HTTPBearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")));
    }
}
