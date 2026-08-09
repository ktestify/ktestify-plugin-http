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

import com.sun.net.httpserver.HttpServer;
import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import io.github.ktestify.config.KtestifyConfig;
import io.github.ktestify.http.entities.KtestifyHttpEndpoint;
import io.github.ktestify.models.ConsumedRecord;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link HttpActionSteps}, exercised against a local loopback {@link HttpServer}. */
@DisplayName("HttpActionSteps")
class HttpActionStepsTest {

    private SharedHttpResources shared;
    private HttpActionSteps steps;
    private HttpServer server;
    private String baseUrl;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        KtestifyConfig.reset();
        shared = new SharedHttpResources();
        steps = new HttpActionSteps(shared);

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/echo", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            byte[] responseBody =
                    requestBody.length > 0 ? requestBody : "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        shared.endpoints.register(
                "test-api",
                KtestifyHttpEndpoint.builder()
                        .endpointAlias("test-api")
                        .baseUrl(baseUrl)
                        .build());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        KtestifyConfig.reset();
    }

    @Nested
    @DisplayName("whenHttpRequestIsSent()")
    class WhenHttpRequestIsSentTests {

        @Test
        @DisplayName("sends the request and stores the response under responseAlias")
        void sendsAndStoresResponse() {
            DataTable dt = buildDataTable(
                    List.of("endpointAlias", "method", "path", "responseAlias"),
                    List.of("test-api", "GET", "/echo", "my-resp"));

            steps.whenHttpRequestIsSent(dt);

            ConsumedRecord<String> response = shared.responses.getOrThrow("my-resp");
            assertEquals("200", response.getAttributes().get("statusCode"));
            assertEquals("{\"ok\":true}", response.getValue());
        }

        @Test
        @DisplayName("sends an inline body")
        void sendsInlineBody() {
            DataTable dt = buildDataTable(
                    List.of("endpointAlias", "method", "path", "body", "responseAlias"),
                    List.of("test-api", "POST", "/echo", "{\"x\":1}", "my-resp"));

            steps.whenHttpRequestIsSent(dt);

            assertEquals("{\"x\":1}", shared.responses.getOrThrow("my-resp").getValue());
        }

        @Test
        @DisplayName("sends a file-based body, resolved against the assets directory")
        void sendsFileBasedBody() throws IOException {
            shared.assetsDirectory = tempDir.toString();
            Files.writeString(tempDir.resolve("payload.json"), "{\"fromFile\":true}");

            DataTable dt = buildDataTable(
                    List.of("endpointAlias", "method", "path", "file", "responseAlias"),
                    List.of("test-api", "POST", "/echo", "payload.json", "my-resp"));

            steps.whenHttpRequestIsSent(dt);

            assertEquals(
                    "{\"fromFile\":true}",
                    shared.responses.getOrThrow("my-resp").getValue());
        }

        @Test
        @DisplayName("appends queryParams to the URL")
        void appendsQueryParams() {
            DataTable dt = buildDataTable(
                    List.of("endpointAlias", "method", "path", "queryParams", "responseAlias"),
                    List.of("test-api", "GET", "/echo", "id=ORD-001,foo=bar", "my-resp"));

            steps.whenHttpRequestIsSent(dt);

            assertEquals(
                    baseUrl + "/echo?id=ORD-001&foo=bar",
                    shared.responses.getOrThrow("my-resp").getSource());
        }

        @Test
        @DisplayName("adds an Authorization header when the endpoint has a bearer token")
        void addsAuthorizationHeaderWhenBearerTokenPresent() {
            shared.endpoints.register(
                    "secured-api",
                    KtestifyHttpEndpoint.builder()
                            .endpointAlias("secured-api")
                            .baseUrl(baseUrl)
                            .bearerToken("s3cr3t")
                            .build());

            DataTable dt = buildDataTable(
                    List.of("endpointAlias", "method", "path", "responseAlias"),
                    List.of("secured-api", "GET", "/echo", "resp2"));

            assertDoesNotThrow(() -> steps.whenHttpRequestIsSent(dt));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when both file and body are supplied")
        void throwsWhenFileAndBodyBothSupplied() {
            DataTable dt = buildDataTable(
                    List.of("endpointAlias", "method", "path", "file", "body", "responseAlias"),
                    List.of("test-api", "POST", "/echo", "x.json", "{}", "resp"));

            assertThrows(IllegalArgumentException.class, () -> steps.whenHttpRequestIsSent(dt));
        }

        @Test
        @DisplayName("throws IllegalStateException when endpointAlias is not registered")
        void throwsWhenEndpointNotRegistered() {
            DataTable dt = buildDataTable(
                    List.of("endpointAlias", "method", "path", "responseAlias"),
                    List.of("unknown", "GET", "/echo", "resp"));

            assertThrows(IllegalStateException.class, () -> steps.whenHttpRequestIsSent(dt));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when a required column is missing")
        void throwsWhenRequiredColumnMissing() {
            DataTable dt = buildDataTable(
                    List.of("endpointAlias", "method", "responseAlias"), List.of("test-api", "GET", "resp"));

            assertThrows(IllegalArgumentException.class, () -> steps.whenHttpRequestIsSent(dt));
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
