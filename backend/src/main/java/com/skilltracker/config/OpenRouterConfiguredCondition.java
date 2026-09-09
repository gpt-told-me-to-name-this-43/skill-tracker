package com.skilltracker.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches only when an OpenRouter API key is actually set. An empty value means "not configured", so
 * the suggestion endpoint answers 503 rather than calling out with an empty credential.
 */
public class OpenRouterConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String apiKey = context.getEnvironment().getProperty("openrouter.api-key");
        return apiKey != null && !apiKey.isBlank();
    }
}
