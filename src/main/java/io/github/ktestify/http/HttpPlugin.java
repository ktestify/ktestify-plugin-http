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

package io.github.ktestify.http;

import com.typesafe.config.Config;
import io.github.ktestify.exceptions.PluginException;
import io.github.ktestify.http.config.HttpConfig;
import io.github.ktestify.plugin.KtestifyPlugin;
import io.github.ktestify.plugin.PluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ktestify plugin entry point for synchronous HTTP request/response testing.
 *
 * <p>Registers itself via the Java {@link java.util.ServiceLoader} mechanism (see
 * {@code META-INF/services/io.github.ktestify.plugin.KtestifyPlugin}).
 *
 * <h2>What this plugin provides</h2>
 *
 * <ul>
 *   <li>A transport layer - {@link io.github.ktestify.http.io.HttpRequestResponseClient} - built entirely on
 *       {@code ktestify-core}'s {@link io.github.ktestify.io.core.RequestResponseClient} contract, wrapping
 *       {@code java.net.http.HttpClient}.
 *   <li>Cucumber step definitions in {@code io.github.ktestify.http.steps} - auto-injected as a {@code --glue} package
 *       by the ktestify runtime.
 * </ul>
 *
 * <h2>Configuration</h2>
 *
 * All settings live under {@code ktestify.plugins.http} in the HOCON config tree. Defaults are declared in the
 * {@code reference.conf} bundled with this JAR. Override any value in your {@code application.conf} or via the
 * corresponding environment variable (see {@link HttpConfig}).
 *
 * @since 0.1.0
 * @see HttpConfig
 * @see io.github.ktestify.http.io.HttpRequestResponseClient
 */
public final class HttpPlugin implements KtestifyPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(HttpPlugin.class);

    private static final String PLUGIN_ID = "http";
    private static final String PLUGIN_VERSION = "0.1.0-SNAPSHOT";
    private static final String PLUGIN_AUTHOR_NAME = "Nil MALHOMME";
    private static final String PLUGIN_AUTHOR_EMAIL = "malhomme.nil+oss@icloud.com";
    private static final String GLUE_PACKAGE = "io.github.ktestify.http.steps";

    /** Cached config - populated during {@link #initialize(PluginContext)}. */
    private HttpConfig config;

    // -------------------------------------------------------------------------
    // KtestifyPlugin contract
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    /** {@inheritDoc} */
    @Override
    public String getVersion() {
        return PLUGIN_VERSION;
    }

    /**
     * Returns the name of the plugin author.
     *
     * @return {@code "Nil MALHOMME"}
     */
    @Override
    public String getAuthorName() {
        return PLUGIN_AUTHOR_NAME;
    }

    /**
     * Returns the contact email of the plugin author.
     *
     * @return {@code "malhomme.nil+oss@icloud.com"}
     */
    @Override
    public String getAuthorEmail() {
        return PLUGIN_AUTHOR_EMAIL;
    }

    /**
     * Returns the Cucumber glue package containing all HTTP step definitions.
     *
     * @return {@code "io.github.ktestify.http.steps"}
     */
    @Override
    public String getGluePackage() {
        return GLUE_PACKAGE;
    }

    /**
     * Initializes the plugin: loads and validates the HTTP configuration.
     *
     * @param context plugin context providing access to the loaded {@link io.github.ktestify.config.KtestifyConfig}
     * @throws PluginException if the config subtree {@code ktestify.plugins.http} is missing
     */
    @Override
    public void initialize(PluginContext context) {
        LOG.info("Initializing ktestify HTTP plugin v{}…", PLUGIN_VERSION);

        Config raw = context.getConfig().getRaw();
        if (!raw.hasPath("ktestify.plugins.http")) {
            throw new PluginException("HTTP plugin: missing HOCON section 'ktestify.plugins.http'. "
                    + "Ensure the plugin JAR (with its reference.conf) is on the classpath.");
        }

        this.config = HttpConfig.from(raw);

        LOG.info(
                "HTTP plugin initialized  -  connect-timeout={}ms, read-timeout={}ms, poll-interval={}ms, "
                        + "follow-redirects={}, trust-all-certificates={}.",
                config.getConnectTimeoutMs(),
                config.getReadTimeoutMs(),
                config.getPollIntervalMs(),
                config.isFollowRedirects(),
                config.isTrustAllCertificates());

        if (config.isTrustAllCertificates()) {
            LOG.warn("HTTP plugin: 'tls.trust-all' is enabled  -  TLS certificate validation is disabled. "
                    + "Use only against local/dev endpoints, never in CI against real environments.");
        }
    }

    /** No-op shutdown - {@code java.net.http.HttpClient} manages its own connection pool lifecycle. */
    @Override
    public void shutdown() {
        LOG.info("HTTP plugin shut down.");
    }
}
