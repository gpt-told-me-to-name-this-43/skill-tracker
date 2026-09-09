package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.skilltracker.config.EnvProxySelector;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * httpx followed the proxy environment variables automatically; the JDK client does not, so the
 * behaviour is reproduced explicitly and pinned here.
 */
class EnvProxySelectorTest {

    private static final String PROXY = "http://host.docker.internal:8118";

    @Test
    void routesHttpAndHttpsThroughTheConfiguredProxies() {
        EnvProxySelector selector = new EnvProxySelector(PROXY, PROXY, null);

        assertThat(proxyHost(selector, "https://api.github.com/repos")).isEqualTo("host.docker.internal:8118");
        assertThat(proxyHost(selector, "http://api.github.com/repos")).isEqualTo("host.docker.internal:8118");
    }

    @Test
    void honoursNoProxyForExactHostsAndSubdomains() {
        EnvProxySelector selector = new EnvProxySelector(PROXY, PROXY, "postgres,redis,localhost,127.0.0.1,.internal");

        assertThat(selector.select(URI.create("http://redis:6379"))).containsExactly(Proxy.NO_PROXY);
        assertThat(selector.select(URI.create("http://localhost:8000"))).containsExactly(Proxy.NO_PROXY);
        assertThat(selector.select(URI.create("https://db.internal/x"))).containsExactly(Proxy.NO_PROXY);
        assertThat(proxyHost(selector, "https://api.github.com")).isEqualTo("host.docker.internal:8118");
    }

    @Test
    void aStarDisablesProxyingEntirely() {
        EnvProxySelector selector = new EnvProxySelector(PROXY, PROXY, "*");

        assertThat(selector.select(URI.create("https://api.github.com"))).containsExactly(Proxy.NO_PROXY);
    }

    @Test
    void aSchemeWithoutAProxyGoesDirect() {
        EnvProxySelector selector = new EnvProxySelector(null, PROXY, null);

        assertThat(selector.select(URI.create("http://api.github.com"))).containsExactly(Proxy.NO_PROXY);
        assertThat(proxyHost(selector, "https://api.github.com")).isEqualTo("host.docker.internal:8118");
    }

    @Test
    void isOnlyInstalledWhenAProxyIsConfigured() {
        assertThat(EnvProxySelector.fromEnvironment(Map.<String, String>of()::get))
                .isEmpty();
        assertThat(EnvProxySelector.fromEnvironment(Map.of("HTTPS_PROXY", PROXY)::get))
                .isPresent();
        assertThat(EnvProxySelector.fromEnvironment(Map.of("http_proxy", PROXY)::get))
                .isPresent();
    }

    @Test
    void aProxyWithoutASchemeDefaultsToHttp() {
        Optional<EnvProxySelector> selector =
                EnvProxySelector.fromEnvironment(Map.of("HTTP_PROXY", "127.0.0.1:3128")::get);

        assertThat(selector).isPresent();
        assertThat(proxyHost(selector.orElseThrow(), "http://api.github.com")).isEqualTo("127.0.0.1:3128");
    }

    private String proxyHost(EnvProxySelector selector, String uri) {
        List<Proxy> proxies = selector.select(URI.create(uri));
        InetSocketAddress address = (InetSocketAddress) proxies.getFirst().address();
        return address.getHostString() + ":" + address.getPort();
    }
}
