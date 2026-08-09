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

import static org.junit.jupiter.api.Assertions.*;

import com.typesafe.config.ConfigFactory;
import io.github.ktestify.config.KtestifyConfig;
import java.util.Map;
import org.junit.jupiter.api.*;

/** Unit tests for {@link HttpConfig}  -  HOCON parsing and timeout / TLS defaults. */
@DisplayName("HttpConfig")
class HttpConfigTest {

    @BeforeEach
    void reset() {
        KtestifyConfig.reset();
    }

    @AfterEach
    void tearDown() {
        KtestifyConfig.reset();
    }

    @Nested
    @DisplayName("default values")
    class DefaultValuesTests {

        @Test
        @DisplayName("loads successfully from reference.conf defaults")
        void loadsFromDefaults() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.empty());
            assertNotNull(cfg);
        }

        @Test
        @DisplayName("connect-timeout defaults to 10 000 ms")
        void connectTimeoutDefaultIs10s() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.empty());
            assertEquals(10_000L, cfg.getConnectTimeoutMs());
        }

        @Test
        @DisplayName("read-timeout defaults to 30 000 ms")
        void readTimeoutDefaultIs30s() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.empty());
            assertEquals(30_000L, cfg.getReadTimeoutMs());
        }

        @Test
        @DisplayName("poll-interval defaults to 500 ms")
        void pollIntervalDefaultIs500ms() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.empty());
            assertEquals(500L, cfg.getPollIntervalMs());
        }

        @Test
        @DisplayName("follow-redirects defaults to true")
        void followRedirectsDefaultIsTrue() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.empty());
            assertTrue(cfg.isFollowRedirects());
        }

        @Test
        @DisplayName("trust-all defaults to false")
        void trustAllDefaultIsFalse() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.empty());
            assertFalse(cfg.isTrustAllCertificates());
        }

        @Test
        @DisplayName("default-headers is empty by default")
        void defaultHeadersEmptyByDefault() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.empty());
            assertTrue(cfg.getDefaultHeaders().isEmpty());
        }
    }

    @Nested
    @DisplayName("overrides")
    class OverrideTests {

        @Test
        @DisplayName("connect-timeout can be overridden")
        void connectTimeoutOverridable() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.parseString("ktestify.plugins.http.connect-timeout = 5s"));
            assertEquals(5_000L, cfg.getConnectTimeoutMs());
        }

        @Test
        @DisplayName("read-timeout can be overridden")
        void readTimeoutOverridable() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.parseString("ktestify.plugins.http.read-timeout = 60s"));
            assertEquals(60_000L, cfg.getReadTimeoutMs());
        }

        @Test
        @DisplayName("follow-redirects can be disabled")
        void followRedirectsOverridable() {
            HttpConfig cfg =
                    HttpConfig.from(ConfigFactory.parseString("ktestify.plugins.http.follow-redirects = false"));
            assertFalse(cfg.isFollowRedirects());
        }

        @Test
        @DisplayName("tls.trust-all can be enabled")
        void trustAllOverridable() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.parseString("ktestify.plugins.http.tls.trust-all = true"));
            assertTrue(cfg.isTrustAllCertificates());
        }

        @Test
        @DisplayName("default-headers are parsed into a Map")
        void defaultHeadersParsed() {
            HttpConfig cfg = HttpConfig.from(ConfigFactory.parseString(
                    "ktestify.plugins.http.default-headers { X-Test = \"abc\", Accept = \"application/json\" }"));
            assertEquals(Map.of("X-Test", "abc", "Accept", "application/json"), cfg.getDefaultHeaders());
        }
    }
}
