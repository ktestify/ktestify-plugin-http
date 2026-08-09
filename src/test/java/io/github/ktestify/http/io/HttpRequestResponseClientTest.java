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

package io.github.ktestify.http.io;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.github.ktestify.exceptions.FetchException;
import io.github.ktestify.models.ConsumedRecord;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HttpRequestResponseClient} against a local {@link HttpServer} loopback instance (no Docker, no
 * external network access - fast and hermetic).
 */
@DisplayName("HttpRequestResponseClient")
class HttpRequestResponseClientTest {

    private HttpServer server;
    private String baseUrl;
    private HttpRequestResponseClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/echo", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("X-Custom", "value");
            byte[] responseBody =
                    requestBody.length > 0 ? requestBody : "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.createContext("/not-found", exchange -> {
            byte[] responseBody = "not found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        client = new HttpRequestResponseClient(HttpClient.newHttpClient(), 5_000L);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        client.close();
    }

    @Nested
    @DisplayName("execute()")
    class ExecuteTests {

        @Test
        @DisplayName("maps a 200 response to a ConsumedRecord with statusCode attribute")
        void mapsSuccessfulResponse() {
            HttpRequestSpec spec = HttpRequestSpec.builder()
                    .method("GET")
                    .url(baseUrl + "/echo")
                    .build();

            List<ConsumedRecord<String>> records = client.execute(spec);

            assertEquals(1, records.size());
            ConsumedRecord<String> record = records.get(0);
            assertEquals(baseUrl + "/echo", record.getSource());
            assertEquals("GET", record.getKey());
            assertEquals(0, record.getPartition());
            assertEquals(-1L, record.getOffset());
            assertEquals("{\"ok\":true}", record.getValue());
            assertEquals("200", record.getAttributes().get(HttpRequestResponseClient.ATTRIBUTE_STATUS_CODE));
            assertNotNull(record.getAttributes().get(HttpRequestResponseClient.ATTRIBUTE_ELAPSED_MS));
            // JDK's HttpClient normalizes header field name casing (e.g. "Content-type"), so compare
            // case-insensitively.
            String contentType = record.getHeaders().entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase("Content-Type"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            assertEquals("application/json", contentType);
        }

        @Test
        @DisplayName("sends the request body and headers, and echoes it back")
        void sendsBodyAndHeaders() {
            HttpRequestSpec spec = HttpRequestSpec.builder()
                    .method("POST")
                    .url(baseUrl + "/echo")
                    .headers(Map.of("X-Test", "abc"))
                    .body("{\"orderId\":\"ORD-001\"}")
                    .build();

            List<ConsumedRecord<String>> records = client.execute(spec);

            assertEquals("{\"orderId\":\"ORD-001\"}", records.get(0).getValue());
        }

        @Test
        @DisplayName("appends query parameters to the URL")
        void appendsQueryParams() {
            HttpRequestSpec spec = HttpRequestSpec.builder()
                    .method("GET")
                    .url(baseUrl + "/echo")
                    .queryParams(Map.of("id", "ORD-001"))
                    .build();

            List<ConsumedRecord<String>> records = client.execute(spec);

            assertEquals(baseUrl + "/echo?id=ORD-001", records.get(0).getSource());
        }

        @Test
        @DisplayName("maps a non-2xx response without throwing  -  status is carried in attributes")
        void mapsErrorResponseWithoutThrowing() {
            HttpRequestSpec spec = HttpRequestSpec.builder()
                    .method("GET")
                    .url(baseUrl + "/not-found")
                    .build();

            List<ConsumedRecord<String>> records = client.execute(spec);

            assertEquals("404", records.get(0).getAttributes().get(HttpRequestResponseClient.ATTRIBUTE_STATUS_CODE));
        }

        @Test
        @DisplayName("throws FetchException when the connection fails")
        void throwsFetchExceptionOnConnectionFailure() {
            HttpRequestSpec spec = HttpRequestSpec.builder()
                    .method("GET")
                    .url("http://localhost:1/unreachable")
                    .build();

            assertThrows(FetchException.class, () -> client.execute(spec));
        }
    }

    @Nested
    @DisplayName("close()")
    class CloseTests {

        @Test
        @DisplayName("close() does not throw and is idempotent")
        void closeIsIdempotent() {
            assertDoesNotThrow(() -> {
                client.close();
                client.close();
            });
        }
    }

    @Nested
    @DisplayName("constructor from HttpConfig")
    class ConfigConstructorTests {

        @Test
        @DisplayName("builds a working client from HttpConfig defaults")
        void buildsFromConfig() {
            io.github.ktestify.http.config.HttpConfig cfg =
                    io.github.ktestify.http.config.HttpConfig.from(com.typesafe.config.ConfigFactory.empty());
            HttpRequestResponseClient configuredClient = new HttpRequestResponseClient(cfg);

            HttpRequestSpec spec = HttpRequestSpec.builder()
                    .method("GET")
                    .url(baseUrl + "/echo")
                    .build();

            assertDoesNotThrow(() -> configuredClient.execute(spec));
            configuredClient.close();
        }
    }
}
