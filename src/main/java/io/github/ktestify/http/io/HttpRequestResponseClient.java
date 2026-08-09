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

import io.github.ktestify.exceptions.FetchException;
import io.github.ktestify.http.config.HttpConfig;
import io.github.ktestify.io.core.RequestResponseClient;
import io.github.ktestify.models.ConsumedRecord;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Transport-layer implementation of {@link RequestResponseClient} for HTTP, wrapping {@link HttpClient} exactly as
 * {@code WebhookNotificationChannel} does in {@code ktestify-plugin-notifications}.
 *
 * <p>A single instance wraps one connection-pooled {@link HttpClient} and is meant to be created once per scenario (see
 * {@link io.github.ktestify.http.steps.SharedHttpResources}), not per request  -  see the client lifecycle note on
 * {@link io.github.ktestify.io.core.AbstractSynchronousConsumer}.
 *
 * <h2>ConsumedRecord field mapping</h2>
 *
 * <table>
 *   <tr><th>ConsumedRecord field</th><th>HTTP source</th></tr>
 *   <tr><td>source</td><td>request URL</td></tr>
 *   <tr><td>partition</td><td>0 (no partitioning concept)</td></tr>
 *   <tr><td>offset</td><td>-1 (no offset concept)</td></tr>
 *   <tr><td>key</td><td>HTTP method (GET, POST, …)</td></tr>
 *   <tr><td>value</td><td>response body as UTF-8 String</td></tr>
 *   <tr><td>timestamp</td><td>instant the response was received</td></tr>
 *   <tr><td>headers</td><td>response HTTP headers</td></tr>
 *   <tr><td>attributes</td><td>{@code {"statusCode": "200", "elapsedMs": "42"}}</td></tr>
 * </table>
 *
 * @since 0.1.0
 * @see HttpRequestSpec
 * @see HttpConsumer
 */
@Slf4j
public class HttpRequestResponseClient implements RequestResponseClient<HttpRequestSpec, String> {

    /** Transport attribute key carrying the HTTP status code (see {@link ConsumedRecord#getAttributes()}). */
    public static final String ATTRIBUTE_STATUS_CODE = "statusCode";

    /** Transport attribute key carrying the round-trip time in milliseconds. */
    public static final String ATTRIBUTE_ELAPSED_MS = "elapsedMs";

    private final HttpClient httpClient;
    private final long readTimeoutMs;

    /**
     * Creates a client backed by the given global plugin config.
     *
     * @param config global plugin config (connect timeout, read timeout, redirect policy, TLS trust settings)
     */
    public HttpRequestResponseClient(HttpConfig config) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .followRedirects(config.isFollowRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);
        this.httpClient = builder.build();
        this.readTimeoutMs = config.getReadTimeoutMs();
    }

    /** Constructor used by tests (and advanced callers) to inject a pre-built {@link HttpClient}. */
    public HttpRequestResponseClient(HttpClient httpClient, long readTimeoutMs) {
        this.httpClient = httpClient;
        this.readTimeoutMs = readTimeoutMs;
    }

    // -------------------------------------------------------------------------
    // RequestResponseClient contract
    // -------------------------------------------------------------------------

    /**
     * Sends the request described by {@code request} and maps the response into a single-element list of
     * {@link ConsumedRecord}{@code <String>} following the field mapping table above.
     *
     * @param request the request to send
     * @return a single-element list containing the response as a {@link ConsumedRecord}{@code <String>}
     * @throws FetchException if the connection fails, times out, or the thread is interrupted
     */
    @Override
    public List<ConsumedRecord<String>> execute(HttpRequestSpec request) throws FetchException {
        String url = appendQueryParams(request.getUrl(), request.getQueryParams());
        log.info("Sending HTTP {} {}…", request.getMethod(), url);

        HttpRequest.Builder builder =
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMillis(readTimeoutMs));

        request.getHeaders().forEach(builder::header);

        String body = request.getBody();
        HttpRequest.BodyPublisher bodyPublisher = body != null
                ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
                : HttpRequest.BodyPublishers.noBody();
        builder.method(request.getMethod().toUpperCase(), bodyPublisher);

        long start = System.currentTimeMillis();
        try {
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsedMs = System.currentTimeMillis() - start;

            log.info("HTTP {} {} → {} ({}ms)", request.getMethod(), url, response.statusCode(), elapsedMs);

            return List.of(toConsumedRecord(request, url, response, elapsedMs));
        } catch (java.io.IOException e) {
            throw new FetchException(
                    "HTTP request failed for " + request.getMethod() + " " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted while sending HTTP request to " + url);
        }
    }

    /**
     * Releases the underlying {@link HttpClient}'s connection pool. {@link HttpClient} does not expose an explicit
     * shutdown API in the JDK, so this is effectively a no-op reserved for future JDK versions / symmetry with
     * {@link RequestResponseClient#close()}.
     */
    @Override
    public void close() {
        log.debug("HttpRequestResponseClient closed (no-op  -  java.net.http.HttpClient has no explicit shutdown).");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static ConsumedRecord<String> toConsumedRecord(
            HttpRequestSpec request, String url, HttpResponse<String> response, long elapsedMs) {
        Map<String, String> headers = flattenHeaders(response);
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(ATTRIBUTE_STATUS_CODE, String.valueOf(response.statusCode()));
        attributes.put(ATTRIBUTE_ELAPSED_MS, String.valueOf(elapsedMs));

        return ConsumedRecord.<String>builder()
                .source(url)
                .partition(0)
                .offset(-1L)
                .key(request.getMethod())
                .value(response.body())
                .timestamp(Instant.now())
                .headers(headers)
                .attributes(attributes)
                .build();
    }

    private static Map<String, String> flattenHeaders(HttpResponse<String> response) {
        Map<String, String> flat = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> flat.put(name, String.join(", ", values)));
        return flat;
    }

    private static String appendQueryParams(String url, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        String query = queryParams.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return url.contains("?") ? url + "&" + query : url + "?" + query;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
