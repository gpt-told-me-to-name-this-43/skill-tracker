package com.skilltracker;

import com.skilltracker.config.GitHubProperties;
import com.skilltracker.config.JwtProperties;
import com.skilltracker.config.OpenRouterProperties;
import com.skilltracker.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({
    JwtProperties.class,
    StorageProperties.class,
    GitHubProperties.class,
    OpenRouterProperties.class
})
public class SkillTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillTrackerApplication.class, args);
    }
}
