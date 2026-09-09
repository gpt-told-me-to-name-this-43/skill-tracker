package com.skilltracker.config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Honours the {@code HTTP_PROXY}, {@code HTTPS_PROXY} and {@code NO_PROXY} variables that the
 * previous httpx-based clients picked up automatically. The JDK HTTP client ignores them by default,
 * so outbound calls would otherwise stop going through a developer's local proxy.
 */
public class EnvProxySelector extends ProxySelector {

    private final URI httpProxy;
    private final URI httpsProxy;
    private final List<String> noProxyHosts;
    private final boolean proxyDisabledForAll;

    public EnvProxySelector(String httpProxy, String httpsProxy, String noProxy) {
        this.httpProxy = parse(httpProxy);
        this.httpsProxy = parse(httpsProxy);

        String rules = noProxy == null ? "" : noProxy.trim();
        this.proxyDisabledForAll = "*".equals(rules);
        this.noProxyHosts = rules.isEmpty()
                ? List.of()
                : java.util.Arrays.stream(rules.split(","))
                        .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
                        .filter(entry -> !entry.isEmpty())
                        .toList();
    }

    public static Optional<EnvProxySelector> fromEnvironment(java.util.function.Function<String, String> lookup) {
        String httpProxy = firstNonBlank(lookup.apply("HTTP_PROXY"), lookup.apply("http_proxy"));
        String httpsProxy = firstNonBlank(lookup.apply("HTTPS_PROXY"), lookup.apply("https_proxy"));
        String noProxy = firstNonBlank(lookup.apply("NO_PROXY"), lookup.apply("no_proxy"));

        if (httpProxy == null && httpsProxy == null) {
            return Optional.empty();
        }
        return Optional.of(new EnvProxySelector(httpProxy, httpsProxy, noProxy));
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (proxyDisabledForAll || isExempt(uri.getHost())) {
            return List.of(Proxy.NO_PROXY);
        }

        URI proxy = "https".equalsIgnoreCase(uri.getScheme()) ? httpsProxy : httpProxy;
        if (proxy == null) {
            return List.of(Proxy.NO_PROXY);
        }

        int port = proxy.getPort() == -1 ? defaultPort(proxy.getScheme()) : proxy.getPort();
        SocketAddress address = InetSocketAddress.createUnresolved(proxy.getHost(), port);
        return List.of(new Proxy(Proxy.Type.HTTP, address));
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException failure) {
        // Nothing to fail over to: the selector offers a single proxy per scheme.
    }

    private boolean isExempt(String host) {
        if (host == null) {
            return false;
        }
        String candidate = host.toLowerCase(Locale.ROOT);
        return noProxyHosts.stream()
                .anyMatch(rule -> candidate.equals(rule)
                        || candidate.endsWith("." + rule)
                        || (rule.startsWith(".") && candidate.endsWith(rule)));
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static URI parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalised = value.contains("://") ? value : "http://" + value;
        URI uri = URI.create(normalised);
        return uri.getHost() == null ? null : uri;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
