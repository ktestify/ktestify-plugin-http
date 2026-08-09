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

import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import io.github.ktestify.config.KtestifyConfig;
import io.github.ktestify.http.entities.KtestifyHttpEndpoint;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link HttpBackgroundSteps}. */
@DisplayName("HttpBackgroundSteps")
class HttpBackgroundStepsTest {

    private SharedHttpResources shared;
    private HttpBackgroundSteps steps;

    @BeforeEach
    void setUp() {
        KtestifyConfig.reset();
        shared = new SharedHttpResources();
        steps = new HttpBackgroundSteps(shared);
    }

    @AfterEach
    void tearDown() {
        KtestifyConfig.reset();
    }

    @Nested
    @DisplayName("givenHttpEndpoint()")
    class GivenHttpEndpointTests {

        @Test
        @DisplayName("registers the endpoint under its alias")
        void registersEndpoint() {
            DataTable dt = buildDataTable(
                    List.of("endpointAlias", "baseUrl"), List.of("orders-api", "http://localhost:8080/api"));

            steps.givenHttpEndpoint(dt);

            KtestifyHttpEndpoint endpoint = shared.endpoints.getOrThrow("orders-api");
            assertEquals("http://localhost:8080/api", endpoint.getBaseUrl());
            assertFalse(endpoint.hasBearerToken());
        }

        @Test
        @DisplayName("throws IllegalArgumentException when endpointAlias is blank")
        void throwsWhenAliasBlank() {
            DataTable dt = buildDataTable(List.of("endpointAlias", "baseUrl"), List.of("", "http://localhost:8080"));

            assertThrows(IllegalArgumentException.class, () -> steps.givenHttpEndpoint(dt));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when baseUrl is blank")
        void throwsWhenBaseUrlBlank() {
            DataTable dt = buildDataTable(List.of("endpointAlias", "baseUrl"), List.of("orders-api", ""));

            assertThrows(IllegalArgumentException.class, () -> steps.givenHttpEndpoint(dt));
        }
    }

    @Nested
    @DisplayName("givenHttpEndpoints()")
    class GivenHttpEndpointsTests {

        @Test
        @DisplayName("registers every row")
        void registersEveryRow() {
            DataTable dt = DataTable.create(
                    List.of(
                            List.of("endpointAlias", "baseUrl"),
                            List.of("a", "http://localhost:1"),
                            List.of("b", "http://localhost:2")),
                    new DataTableTypeRegistryTableConverter(new DataTableTypeRegistry(Locale.ENGLISH)));

            steps.givenHttpEndpoints(dt);

            assertEquals("http://localhost:1", shared.endpoints.getOrThrow("a").getBaseUrl());
            assertEquals("http://localhost:2", shared.endpoints.getOrThrow("b").getBaseUrl());
        }
    }

    @Nested
    @DisplayName("givenHttpBearerToken()")
    class GivenHttpBearerTokenTests {

        @Test
        @DisplayName("attaches a bearer token to a previously registered endpoint")
        void attachesBearerToken() {
            shared.endpoints.register(
                    "orders-api",
                    KtestifyHttpEndpoint.builder()
                            .endpointAlias("orders-api")
                            .baseUrl("http://localhost:8080")
                            .build());

            DataTable dt = buildDataTable(List.of("endpointAlias", "token"), List.of("orders-api", "abc123"));

            steps.givenHttpBearerToken(dt);

            KtestifyHttpEndpoint endpoint = shared.endpoints.getOrThrow("orders-api");
            assertTrue(endpoint.hasBearerToken());
            assertEquals("abc123", endpoint.getBearerToken());
        }

        @Test
        @DisplayName("resolves dynamic variables in the token value")
        void resolvesDynamicVariables() {
            shared.endpoints.register(
                    "orders-api",
                    KtestifyHttpEndpoint.builder()
                            .endpointAlias("orders-api")
                            .baseUrl("http://localhost:8080")
                            .build());

            // {{UNKNOWNVAR}} is not a registered dynamic variable  -  DynamicVariableProcessor keeps it unchanged.
            DataTable dt = buildDataTable(List.of("endpointAlias", "token"), List.of("orders-api", "{{UNKNOWNVAR}}"));

            steps.givenHttpBearerToken(dt);

            assertEquals(
                    "{{UNKNOWNVAR}}", shared.endpoints.getOrThrow("orders-api").getBearerToken());
        }

        @Test
        @DisplayName("throws IllegalStateException when the endpoint is not registered")
        void throwsWhenEndpointNotRegistered() {
            DataTable dt = buildDataTable(List.of("endpointAlias", "token"), List.of("unknown", "abc"));

            assertThrows(IllegalStateException.class, () -> steps.givenHttpBearerToken(dt));
        }
    }

    @Nested
    @DisplayName("givenHttpAssetsDirectory()")
    class GivenHttpAssetsDirectoryTests {

        @Test
        @DisplayName("overrides the shared assets directory")
        void overridesAssetsDirectory() {
            DataTable dt = buildDataTable(List.of("absolutePath"), List.of("/base/dir"));

            steps.givenHttpAssetsDirectory(dt);

            assertEquals("/base/dir", shared.assetsDirectory);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when absolutePath is blank")
        void throwsWhenPathBlank() {
            DataTable dt = buildDataTable(List.of("absolutePath"), List.of(""));

            assertThrows(IllegalArgumentException.class, () -> steps.givenHttpAssetsDirectory(dt));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DataTable buildDataTable(List<String> headers, List<String> values) {
        DataTableTypeRegistry registry = new DataTableTypeRegistry(Locale.ENGLISH);
        DataTableTypeRegistryTableConverter converter = new DataTableTypeRegistryTableConverter(registry);
        return DataTable.create(List.of(headers, values), converter);
    }
}
