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

import static org.junit.jupiter.api.Assertions.*;

import io.github.ktestify.config.KtestifyConfig;
import io.github.ktestify.plugin.PluginContext;
import org.junit.jupiter.api.*;

/**
 * Unit tests for {@link HttpPlugin}  -  lifecycle, metadata, and configuration validation.
 *
 * <p>These tests do not perform any real HTTP call; they only exercise config loading and plugin contract methods.
 */
@DisplayName("HttpPlugin")
class HttpPluginTest {

    private HttpPlugin plugin;
    private PluginContext ctx;

    @BeforeEach
    void setUp() {
        KtestifyConfig.reset();
        plugin = new HttpPlugin();
        ctx = KtestifyConfig::getOrLoad;
    }

    @AfterEach
    void tearDown() {
        KtestifyConfig.reset();
    }

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("getId() returns 'http'")
        void idIsHttp() {
            assertEquals("http", plugin.getId());
        }

        @Test
        @DisplayName("getVersion() is non-blank")
        void versionIsNonBlank() {
            assertNotNull(plugin.getVersion());
            assertFalse(plugin.getVersion().isBlank());
        }

        @Test
        @DisplayName("getAuthorName() returns 'Nil MALHOMME'")
        void authorNameIsSet() {
            assertEquals("Nil MALHOMME", plugin.getAuthorName());
        }

        @Test
        @DisplayName("getAuthorEmail() returns the expected email")
        void authorEmailIsSet() {
            assertEquals("malhomme.nil+oss@icloud.com", plugin.getAuthorEmail());
        }

        @Test
        @DisplayName("getGluePackage() returns the steps package")
        void gluePackageIsStepsPackage() {
            assertEquals("io.github.ktestify.http.steps", plugin.getGluePackage());
        }
    }

    @Nested
    @DisplayName("initialize()")
    class InitializeTests {

        @Test
        @DisplayName("initialize() succeeds with default configuration")
        void initializeSucceedsWithDefaults() {
            assertDoesNotThrow(() -> plugin.initialize(ctx));
        }

        @Test
        @DisplayName("initialize() succeeds when tls.trust-all is enabled (warn only)")
        void initializeSucceedsWithTrustAll() {
            KtestifyConfig cfg = KtestifyConfig.load(
                    com.typesafe.config.ConfigFactory.parseString("ktestify.plugins.http.tls.trust-all = true"));
            assertDoesNotThrow(() -> plugin.initialize(() -> cfg));
        }
    }

    @Nested
    @DisplayName("shutdown()")
    class ShutdownTests {

        @Test
        @DisplayName("shutdown() before initialize() does not throw")
        void shutdownBeforeInitDoesNotThrow() {
            assertDoesNotThrow(plugin::shutdown);
        }

        @Test
        @DisplayName("shutdown() after initialize() does not throw")
        void shutdownAfterInitDoesNotThrow() {
            plugin.initialize(ctx);
            assertDoesNotThrow(plugin::shutdown);
        }
    }
}
