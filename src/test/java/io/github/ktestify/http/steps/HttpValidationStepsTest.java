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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link HttpValidationSteps}. */
@DisplayName("HttpValidationSteps")
class HttpValidationStepsTest {

    private SharedHttpResources shared;
    private HttpValidationSteps steps;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        KtestifyConfig.reset();
        shared = new SharedHttpResources();
        steps = new HttpValidationSteps(shared);
    }

    @AfterEach
    void tearDown() {
        KtestifyConfig.reset();
    }

    private static ConsumedRecord<String> response(String body, String statusCode, Map<String, String> headers) {
        return ConsumedRecord.<String>builder()
                .source("http://localhost/orders")
                .partition(0)
                .offset(-1L)
                .key("GET")
                .value(body)
                .timestamp(Instant.now())
                .headers(headers)
                .attributes(Map.of("statusCode", statusCode))
                .build();
    }

    @Nested
    @DisplayName("thenExpectedHttpResponseStatus()")
    class StatusTests {

        @Test
        @DisplayName("passes when the status code matches")
        void passesWhenStatusMatches() {
            shared.responses.register("resp", response("{}", "200", Map.of()));

            DataTable dt = buildDataTable(List.of("responseAlias", "statusCode"), List.of("resp", "200"));

            assertDoesNotThrow(() -> steps.thenExpectedHttpResponseStatus(dt));
        }

        @Test
        @DisplayName("throws AssertionError when the status code does not match")
        void throwsWhenStatusMismatches() {
            shared.responses.register("resp", response("{}", "500", Map.of()));

            DataTable dt = buildDataTable(List.of("responseAlias", "statusCode"), List.of("resp", "200"));

            assertThrows(AssertionError.class, () -> steps.thenExpectedHttpResponseStatus(dt));
        }

        @Test
        @DisplayName("throws IllegalStateException when responseAlias is not registered")
        void throwsWhenResponseNotRegistered() {
            DataTable dt = buildDataTable(List.of("responseAlias", "statusCode"), List.of("unknown", "200"));

            assertThrows(IllegalStateException.class, () -> steps.thenExpectedHttpResponseStatus(dt));
        }
    }

    @Nested
    @DisplayName("thenExpectedHttpResponseBodyFromFile()")
    class BodyFromFileTests {

        @Test
        @DisplayName("passes when the body matches the expected file")
        void passesWhenBodyMatches() throws IOException {
            String json = "{\"orderId\":\"ORD-001\",\"status\":\"validated\"}";
            shared.responses.register("resp", response(json, "200", Map.of()));

            shared.assetsDirectory = tempDir.toString();
            Path expectedFile = tempDir.resolve("expected.json");
            Files.writeString(expectedFile, json);

            DataTable dt = buildDataTable(List.of("responseAlias", "file"), List.of("resp", "expected.json"));

            assertDoesNotThrow(() -> steps.thenExpectedHttpResponseBodyFromFile(dt));
        }

        @Test
        @DisplayName("throws AssertionError when the body does not match")
        void throwsWhenBodyMismatches() throws IOException {
            shared.responses.register("resp", response("{\"a\":1}", "200", Map.of()));

            shared.assetsDirectory = tempDir.toString();
            Files.writeString(tempDir.resolve("expected.json"), "{\"a\":2}");

            DataTable dt = buildDataTable(List.of("responseAlias", "file"), List.of("resp", "expected.json"));

            assertThrows(AssertionError.class, () -> steps.thenExpectedHttpResponseBodyFromFile(dt));
        }
    }

    @Nested
    @DisplayName("andHttpResponseHeaderShouldMatch()")
    class HeaderTests {

        @Test
        @DisplayName("passes when the header value matches")
        void passesWhenHeaderMatches() {
            shared.responses.register("resp", response("{}", "200", Map.of("Content-Type", "application/json")));

            DataTable dt = buildDataTable(
                    List.of("responseAlias", "header", "value"), List.of("resp", "Content-Type", "application/json"));

            assertDoesNotThrow(() -> steps.andHttpResponseHeaderShouldMatch(dt));
        }

        @Test
        @DisplayName("throws AssertionError when the header value does not match")
        void throwsWhenHeaderMismatches() {
            shared.responses.register("resp", response("{}", "200", Map.of("Content-Type", "text/plain")));

            DataTable dt = buildDataTable(
                    List.of("responseAlias", "header", "value"), List.of("resp", "Content-Type", "application/json"));

            assertThrows(AssertionError.class, () -> steps.andHttpResponseHeaderShouldMatch(dt));
        }
    }

    @Nested
    @DisplayName("thenHttpEndpointShouldEventuallyReturn()")
    class EventuallyReturnTests {

        @Test
        @DisplayName("passes once the endpoint starts returning the expected status")
        void passesOnceStatusMatches() throws IOException {
            AtomicInteger callCount = new AtomicInteger(0);
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/status", exchange -> {
                int status = callCount.incrementAndGet() < 2 ? 503 : 200;
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            try {
                String baseUrl = "http://localhost:" + server.getAddress().getPort();
                shared.endpoints.register(
                        "eventually-api",
                        KtestifyHttpEndpoint.builder()
                                .endpointAlias("eventually-api")
                                .baseUrl(baseUrl)
                                .build());

                DataTable dt = buildDataTable(
                        List.of("endpointAlias", "method", "path", "expectedStatus", "readTimeout"),
                        List.of("eventually-api", "GET", "/status", "200", "5"));

                assertDoesNotThrow(() -> steps.thenHttpEndpointShouldEventuallyReturn(dt));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("throws AssertionError when the endpoint never returns the expected status within the timeout")
        void throwsWhenNeverMatches() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/status", exchange -> {
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            try {
                String baseUrl = "http://localhost:" + server.getAddress().getPort();
                shared.endpoints.register(
                        "eventually-api-2",
                        KtestifyHttpEndpoint.builder()
                                .endpointAlias("eventually-api-2")
                                .baseUrl(baseUrl)
                                .build());

                DataTable dt = buildDataTable(
                        List.of("endpointAlias", "method", "path", "expectedStatus", "readTimeout"),
                        List.of("eventually-api-2", "GET", "/status", "200", "1"));

                assertThrows(AssertionError.class, () -> steps.thenHttpEndpointShouldEventuallyReturn(dt));
            } finally {
                server.stop(0);
            }
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
