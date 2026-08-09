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

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.When;
import io.github.ktestify.http.entities.KtestifyHttpEndpoint;
import io.github.ktestify.http.io.HttpRequestSpec;
import io.github.ktestify.io.inputs.DynamicVariableProcessor;
import io.github.ktestify.models.ConsumedRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Cucumber {@code @When} step definitions for HTTP actions.
 *
 * <h2>Available steps</h2>
 *
 * <pre>{@code
 * When HTTP request is sent
 *   | endpointAlias | method | path             | file        | responseAlias |
 *   | orders-api    | POST   | /orders/validate | order.json  | validate-resp |
 *
 * When HTTP request is sent
 *   | endpointAlias | method | path         | queryParams | responseAlias |
 *   | orders-api    | GET    | /orders/{id} | id=ORD-001  | fetch-resp    |
 * }</pre>
 *
 * <p>The {@code file} path is resolved against the configured assets directory when it is relative. Either {@code file}
 * or an inline {@code body} column may be supplied, not both. {@code queryParams} is a comma-separated list of
 * {@code key=value} pairs.
 *
 * <p>Sends the request through the scenario's single shared {@code HttpRequestResponseClient} (see
 * {@link SharedHttpResources}) and stores the resulting {@link ConsumedRecord}{@code <String>} under
 * {@code responseAlias} for later validation steps.
 *
 * @since 0.1.0
 */
@Slf4j
public class HttpActionSteps {

    private final SharedHttpResources shared;
    private final DynamicVariableProcessor dynamicVariableProcessor = new DynamicVariableProcessor();

    /** PicoContainer constructor injection. */
    public HttpActionSteps(SharedHttpResources shared) {
        this.shared = shared;
    }

    // -------------------------------------------------------------------------
    // Step definitions
    // -------------------------------------------------------------------------

    /**
     * Sends an HTTP request and stores the response under {@code responseAlias}.
     *
     * <p>DataTable columns:
     *
     * <table>
     *   <tr><th>Column</th><th>Required</th><th>Description</th></tr>
     *   <tr><td>endpointAlias</td><td>yes</td><td>Alias of a previously registered endpoint</td></tr>
     *   <tr><td>method</td><td>yes</td><td>HTTP method (GET, POST, PUT, DELETE, …)</td></tr>
     *   <tr><td>path</td><td>yes</td><td>Path appended to the endpoint's base URL</td></tr>
     *   <tr><td>file</td><td>no</td><td>Local file whose content becomes the request body</td></tr>
     *   <tr><td>body</td><td>no</td><td>Inline request body (mutually exclusive with {@code file})</td></tr>
     *   <tr><td>queryParams</td><td>no</td><td>Comma-separated {@code key=value} pairs appended to the URL</td></tr>
     *   <tr><td>responseAlias</td><td>yes</td><td>Alias under which the response is stored</td></tr>
     * </table>
     *
     * @param dataTable one-row DataTable defining the request
     */
    @When("HTTP request is sent")
    public void whenHttpRequestIsSent(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);

        String endpointAlias = getRequired(row, "endpointAlias");
        String method = getRequired(row, "method");
        String path = getRequired(row, "path");
        String responseAlias = getRequired(row, "responseAlias");

        KtestifyHttpEndpoint endpoint = shared.endpoints.getOrThrow(endpointAlias);

        String url = buildUrl(endpoint.getBaseUrl(), path);
        Map<String, String> headers = buildHeaders(endpoint);
        Map<String, String> queryParams = parseQueryParams(row.get("queryParams"));
        String body = resolveBody(row);

        HttpRequestSpec spec = HttpRequestSpec.builder()
                .method(method.toUpperCase())
                .url(url)
                .headers(headers)
                .queryParams(queryParams)
                .body(body)
                .build();

        log.info("Sending HTTP {} request to '{}' (alias '{}')…", method.toUpperCase(), url, endpointAlias);

        ConsumedRecord<String> response = shared.client.execute(spec).get(0);
        shared.responses.register(responseAlias, response);

        log.info(
                "HTTP response captured under alias '{}'  -  statusCode={}.",
                responseAlias,
                response.getAttributes().get("statusCode"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveBody(Map<String, String> row) {
        String file = getString(row, "file");
        String inlineBody = getString(row, "body");

        if (file != null && inlineBody != null) {
            throw new IllegalArgumentException("Only one of 'file' or 'body' may be supplied, not both.");
        }
        if (file != null) {
            String resolvedFile = resolve(shared.assetsDirectory, file);
            try {
                return Files.readString(Path.of(resolvedFile), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to read request body file '" + resolvedFile + "': " + e.getMessage(), e);
            }
        }
        if (inlineBody != null) {
            return dynamicVariableProcessor.process(inlineBody);
        }
        return null;
    }

    private Map<String, String> buildHeaders(KtestifyHttpEndpoint endpoint) {
        Map<String, String> headers = new LinkedHashMap<>(shared.config.getDefaultHeaders());
        headers.putAll(endpoint.getDefaultHeaders());
        if (endpoint.hasBearerToken()) {
            headers.put("Authorization", "Bearer " + endpoint.getBearerToken());
        }
        return headers;
    }

    private static String buildUrl(String baseUrl, String path) {
        if (path == null || path.isBlank()) {
            return baseUrl;
        }
        boolean baseEndsWithSlash = baseUrl.endsWith("/");
        boolean pathStartsWithSlash = path.startsWith("/");
        if (baseEndsWithSlash && pathStartsWithSlash) {
            return baseUrl + path.substring(1);
        }
        if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    private static Map<String, String> parseQueryParams(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> params = new LinkedHashMap<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(pair -> {
                    int idx = pair.indexOf('=');
                    if (idx < 0) {
                        throw new IllegalArgumentException(
                                "Malformed queryParams entry '" + pair + "', expected 'key=value'.");
                    }
                    params.put(
                            pair.substring(0, idx).trim(),
                            pair.substring(idx + 1).trim());
                });
        return params;
    }

    private static String getString(Map<String, String> row, String col) {
        String v = row.get(col);
        return (v != null && !v.isBlank()) ? v : null;
    }

    private static String getRequired(Map<String, String> row, String col) {
        String v = getString(row, col);
        if (v == null) {
            throw new IllegalArgumentException("Required DataTable column '" + col + "' is missing.");
        }
        return v;
    }

    private static String resolve(String assetsDir, String path) {
        if (assetsDir == null || assetsDir.isBlank() || path == null) return path;
        if (Path.of(path).isAbsolute()) return path;
        return Path.of(assetsDir, path).toString();
    }
}
