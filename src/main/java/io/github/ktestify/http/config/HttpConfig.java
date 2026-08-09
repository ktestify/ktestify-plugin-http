/*
 * Copyright 2026 Nil MALHOMME (malhomme.nil+oss@icloud.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.ktestify.http.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Typed configuration for the HTTP plugin.
 *
 * <p>Reads the {@code ktestify.plugins.http} HOCON subtree. Values can be overridden via environment variables (see
 * {@code reference.conf} in this module).
 *
 * <h2>Environment variables</h2>
 *
 * <table>
 *   <tr><th>Key</th><th>Env var</th><th>Default</th></tr>
 *   <tr><td>connect-timeout</td><td>KTESTIFY_HTTP_CONNECT_TIMEOUT</td><td>10s</td></tr>
 *   <tr><td>read-timeout</td><td>KTESTIFY_HTTP_READ_TIMEOUT</td><td>30s</td></tr>
 *   <tr><td>poll-interval</td><td> - </td><td>500ms</td></tr>
 *   <tr><td>follow-redirects</td><td> - </td><td>true</td></tr>
 *   <tr><td>tls.trust-all</td><td> - </td><td>false</td></tr>
 * </table>
 *
 * @since 0.1.0
 */
@Getter
public final class HttpConfig {

    private static final String CONFIG_PATH = "ktestify.plugins.http";

    /** Maximum time in milliseconds to wait while establishing the TCP/TLS connection. Defaults to 10 000 ms. */
    private final long connectTimeoutMs;

    /** Maximum time in milliseconds to wait for the full response to be received. Defaults to 30 000 ms. */
    private final long readTimeoutMs;

    /** Interval in milliseconds between attempts for the "eventually consistent" polling step. Defaults to 500 ms. */
    private final long pollIntervalMs;

    /** Whether the underlying {@link java.net.http.HttpClient} should automatically follow HTTP redirects. */
    private final boolean followRedirects;

    /** Default headers merged into every request, overridden by per-endpoint and per-request headers. */
    private final Map<String, String> defaultHeaders;

    /**
     * When {@code true}, TLS certificate validation is disabled for the underlying client. Explicit opt-in only,
     * intended for local / development endpoints  -  never enable in CI against real environments.
     */
    private final boolean trustAllCertificates;

    private HttpConfig(Config cfg) {
        this.connectTimeoutMs = cfg.getDuration("connect-timeout").toMillis();
        this.readTimeoutMs = cfg.getDuration("read-timeout").toMillis();
        this.pollIntervalMs = cfg.getDuration("poll-interval").toMillis();
        this.followRedirects = cfg.getBoolean("follow-redirects");
        this.defaultHeaders = extractHeaders(cfg);
        this.trustAllCertificates = cfg.getConfig("tls").getBoolean("trust-all");
    }

    /**
     * Parses the plugin config from the full application {@link Config} object. Typically called as:
     *
     * <pre>
     * HttpConfig cfg = HttpConfig.from(ctx.getConfig().getRaw());
     * </pre>
     *
     * @param root the root application config (the full {@code ktestify.*} tree)
     * @return a populated {@code HttpConfig}
     */
    public static HttpConfig from(Config root) {
        Config merged = root.withFallback(ConfigFactory.load()).resolve();
        return new HttpConfig(merged.getConfig(CONFIG_PATH));
    }

    private static Map<String, String> extractHeaders(Config cfg) {
        if (!cfg.hasPath("default-headers")) {
            return Collections.emptyMap();
        }
        Config headersConfig = cfg.getConfig("default-headers");
        if (headersConfig.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headersConfig.entrySet().forEach(entry -> headers.put(entry.getKey(), headersConfig.getString(entry.getKey())));
        return Collections.unmodifiableMap(headers);
    }
}
