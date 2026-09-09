package com.skilltracker.config;

import com.skilltracker.domain.MemberStatus;
import com.skilltracker.domain.TaskStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Reads query parameters using the wire values of the enums.
 *
 * <p>Spring's default enum conversion goes through {@code Enum.valueOf}, which would only accept
 * {@code IN_PROGRESS} and reject the {@code in_progress} the client actually sends.
 */
@Configuration(proxyBeanMethods = false)
public class EnumConverters implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToTaskStatusConverter());
        registry.addConverter(new StringToMemberStatusConverter());
    }

    static class StringToTaskStatusConverter implements Converter<String, TaskStatus> {
        @Override
        public TaskStatus convert(String source) {
            return TaskStatus.fromValue(source);
        }
    }

    static class StringToMemberStatusConverter implements Converter<String, MemberStatus> {
        @Override
        public MemberStatus convert(String source) {
            return MemberStatus.fromValue(source);
        }
    }
}
