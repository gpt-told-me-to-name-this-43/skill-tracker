package com.skilltracker.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Outbound HTTP defaults for the GitHub and OpenRouter integrations. */
@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    /** httpx used a five second default; the ML call was given the longer explicit budget. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration ML_READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Primary
    RestClient.Builder restClientBuilder(Environment environment) {
        HttpClient.Builder httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT);
        EnvProxySelector.fromEnvironment(environment::getProperty).ifPresent(httpClient::proxy);

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient.build());
        requestFactory.setReadTimeout(DEFAULT_READ_TIMEOUT);

        return RestClient.builder().requestFactory(requestFactory);
    }

    /** The ML provider is slower than the other integrations and gets a longer read budget. */
    @Bean("mlRestClientBuilder")
    RestClient.Builder mlRestClientBuilder(Environment environment) {
        HttpClient.Builder httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT);
        EnvProxySelector.fromEnvironment(environment::getProperty).ifPresent(httpClient::proxy);

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient.build());
        requestFactory.setReadTimeout(ML_READ_TIMEOUT);

        return RestClient.builder().requestFactory(requestFactory);
    }
}
