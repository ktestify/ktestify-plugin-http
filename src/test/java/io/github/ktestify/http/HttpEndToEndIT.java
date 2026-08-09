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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import io.github.ktestify.config.KtestifyConfig;
import io.github.ktestify.http.steps.HttpActionSteps;
import io.github.ktestify.http.steps.HttpBackgroundSteps;
import io.github.ktestify.http.steps.HttpValidationSteps;
import io.github.ktestify.http.steps.SharedHttpResources;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end integration test exercising the full HTTP plugin stack (background → action → validation steps) against a
 * real {@link HttpServer} loopback instance, exactly the scenario described in §5.4 of the design document.
 *
 * <p>No Docker / Testcontainers dependency is required  -  {@code com.sun.net.httpserver.HttpServer} is JDK-native and
 * gives a fast, hermetic local HTTP endpoint to test against.
 */
@DisplayName("HTTP plugin  -  end to end")
class HttpEndToEndIT {

    private HttpServer server;
    private String baseUrl;

    private SharedHttpResources shared;
    private HttpBackgroundSteps backgroundSteps;
    private HttpActionSteps actionSteps;
    private HttpValidationSteps validationSteps;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        KtestifyConfig.reset();

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

        // Simulates an "order validation" endpoint: echoes the request body back with a 200,
        // and requires a bearer token.
        server.createContext("/api/orders/validate", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !auth.equals("Bearer test-token-123")) {
                byte[] body = "unauthorized".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(401, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
                return;
            }
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, requestBody.length);
            exchange.getResponseBody().write(requestBody);
            exchange.close();
        });

        // Simulates an "eventually consistent" status endpoint: returns 202 for the first two
        // calls, then 200.
        AtomicInteger statusCallCount = new AtomicInteger(0);
        server.createContext("/api/orders/ORD-001/status", exchange -> {
            int status = statusCallCount.incrementAndGet() <= 2 ? 202 : 200;
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/api";

        shared = new SharedHttpResources();
        backgroundSteps = new HttpBackgroundSteps(shared);
        actionSteps = new HttpActionSteps(shared);
        validationSteps = new HttpValidationSteps(shared);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        KtestifyConfig.reset();
    }

    @Test
    @DisplayName(
            "registers an endpoint, sends a bearer-token-authenticated request, and validates status + body + header")
    void fullSendAndAssertScenario() throws IOException {
        // Given HTTP endpoint
        backgroundSteps.givenHttpEndpoint(row(List.of("endpointAlias", "baseUrl"), List.of("orders-api", baseUrl)));

        // Given HTTP bearer token
        backgroundSteps.givenHttpBearerToken(
                row(List.of("endpointAlias", "token"), List.of("orders-api", "test-token-123")));

        // Given HTTP assets directory
        backgroundSteps.givenHttpAssetsDirectory(row(List.of("absolutePath"), List.of(tempDir.toString())));

        String requestJson = "{\"orderId\":\"ORD-001\",\"status\":\"validated\"}";
        Files.writeString(tempDir.resolve("order.json"), requestJson);
        Files.writeString(tempDir.resolve("expected.json"), requestJson);

        // When HTTP request is sent
        actionSteps.whenHttpRequestIsSent(row(
                List.of("endpointAlias", "method", "path", "file", "responseAlias"),
                List.of("orders-api", "POST", "/orders/validate", "order.json", "validate-resp")));

        // Then expected HTTP response status
        assertDoesNotThrow(() -> validationSteps.thenExpectedHttpResponseStatus(
                row(List.of("responseAlias", "statusCode"), List.of("validate-resp", "200"))));

        // Then expected HTTP response body from file
        assertDoesNotThrow(() -> validationSteps.thenExpectedHttpResponseBodyFromFile(
                row(List.of("responseAlias", "file"), List.of("validate-resp", "expected.json"))));

        // And HTTP response header should match
        assertDoesNotThrow(() -> validationSteps.andHttpResponseHeaderShouldMatch(row(
                List.of("responseAlias", "header", "value"),
                List.of("validate-resp", "Content-Type", "application/json"))));
    }

    @Test
    @DisplayName("rejects requests without a valid bearer token")
    void rejectsUnauthenticatedRequests() {
        backgroundSteps.givenHttpEndpoint(row(List.of("endpointAlias", "baseUrl"), List.of("orders-api", baseUrl)));
        // No bearer token registered this time.

        actionSteps.whenHttpRequestIsSent(row(
                List.of("endpointAlias", "method", "path", "body", "responseAlias"),
                List.of("orders-api", "POST", "/orders/validate", "{}", "unauth-resp")));

        assertThrows(
                AssertionError.class,
                () -> validationSteps.thenExpectedHttpResponseStatus(
                        row(List.of("responseAlias", "statusCode"), List.of("unauth-resp", "200"))));
    }

    @Test
    @DisplayName("HTTP endpoint should eventually return  -  polls until the expected status appears")
    void eventuallyReturnsExpectedStatus() {
        backgroundSteps.givenHttpEndpoint(row(List.of("endpointAlias", "baseUrl"), List.of("orders-api", baseUrl)));

        assertDoesNotThrow(() -> validationSteps.thenHttpEndpointShouldEventuallyReturn(row(
                List.of("endpointAlias", "method", "path", "expectedStatus", "readTimeout"),
                List.of("orders-api", "GET", "/orders/ORD-001/status", "200", "10"))));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DataTable row(List<String> headers, List<String> values) {
        DataTableTypeRegistry registry = new DataTableTypeRegistry(Locale.ENGLISH);
        DataTableTypeRegistryTableConverter converter = new DataTableTypeRegistryTableConverter(registry);
        return DataTable.create(List.of(headers, values), converter);
    }
}
