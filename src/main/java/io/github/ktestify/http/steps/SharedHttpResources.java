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

package io.github.ktestify.http.steps;

import io.github.ktestify.config.KtestifyConfig;
import io.github.ktestify.http.config.HttpConfig;
import io.github.ktestify.http.entities.KtestifyHttpEndpoint;
import io.github.ktestify.http.io.HttpRequestResponseClient;
import io.github.ktestify.manager.ObjectManager;
import io.github.ktestify.models.ConsumedRecord;

/**
 * PicoContainer-managed shared state for the HTTP plugin steps.
 *
 * <p>A single instance is created per Cucumber scenario by PicoContainer and injected into every HTTP step class that
 * declares it as a constructor parameter. This ensures the endpoint registry, response registry, and the shared
 * {@link HttpRequestResponseClient} share the same lifecycle as the scenario, consistent with the client lifecycle
 * documented on {@code AbstractSynchronousConsumer}.
 *
 * <h2>Assets directory</h2>
 *
 * <p>{@link #assetsDirectory} is pre-populated from {@code ktestify.framework.directories.assets} in the loaded config,
 * mirroring {@code SharedAzureBlobResources}. Because this class only depends on {@code ktestify-core}, it reads the
 * config directly rather than importing {@code SharedStepsResources} from {@code ktestify-cucumber}.
 *
 * @since 0.1.0
 */
public class SharedHttpResources {

    /** Registry for HTTP endpoints, keyed by alias. Populated by {@link HttpBackgroundSteps}. */
    public final ObjectManager<KtestifyHttpEndpoint> endpoints = new ObjectManager<>();

    /** Registry for captured responses, keyed by {@code responseAlias}. Populated by {@link HttpActionSteps}. */
    public final ObjectManager<ConsumedRecord<String>> responses = new ObjectManager<>();

    /** Global plugin configuration loaded once per scenario instance. */
    public final HttpConfig config;

    /**
     * One shared, connection-pooled {@link HttpRequestResponseClient} reused for every request in the scenario. Not
     * created and discarded per request - see the client lifecycle note on {@code AbstractSynchronousConsumer}.
     */
    public final HttpRequestResponseClient client;

    /**
     * The assets base directory for the current scenario. Pre-populated from
     * {@code ktestify.framework.directories.assets}; may be {@code null} if not configured.
     */
    public String assetsDirectory;

    /** Initialised by PicoContainer at the start of each scenario. */
    public SharedHttpResources() {
        KtestifyConfig cfg = KtestifyConfig.getOrLoad();
        this.config = HttpConfig.from(cfg.getRaw());
        this.client = new HttpRequestResponseClient(config);

        cfg.getFramework()
                .getAssetsDirectory()
                .filter(path -> !path.isBlank())
                .ifPresent(path -> this.assetsDirectory = path);
    }
}
