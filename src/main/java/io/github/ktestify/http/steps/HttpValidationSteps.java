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
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.github.ktestify.http.entities.KtestifyHttpEndpoint;
import io.github.ktestify.http.io.HttpConsumer;
import io.github.ktestify.http.io.HttpConsumerContext;
import io.github.ktestify.http.io.HttpRequestResponseClient;
import io.github.ktestify.http.io.HttpRequestSpec;
import io.github.ktestify.io.core.PollingRequestResponseClient;
import io.github.ktestify.match.MatchContext;
import io.github.ktestify.match.MatchResult;
import io.github.ktestify.match.RecordMatcher;
import io.github.ktestify.match.RecordMatcherFactory;
import io.github.ktestify.models.ConsumedRecord;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Cucumber {@code @Then} and {@code @And} step definitions for HTTP validations.
 *
 * <p>All body and attribute assertions are performed with unchanged, existing {@code ktestify-core} matchers
 * ({@link RecordMatcherFactory}) - this class contains zero HTTP-specific matching logic (see §5.5 of the design
 * document). The only exception is the header assertion, a direct String comparison since header expectations are not
 * modeled as {@link io.github.ktestify.models.ConsumedRecord#getAttributes()}.
 *
 * <h2>Available steps</h2>
 *
 * <pre>{@code
 * Then expected HTTP response status
 *   | responseAlias | statusCode |
 *   | validate-resp | 200        |
 *
 * Then expected HTTP response body from file
 *   | responseAlias | file          | excludedKeys |
 *   | validate-resp | expected.json | timestamp,id |
 *
 * And HTTP response header should match
 *   | responseAlias | header       | value            |
 *   | validate-resp | Content-Type | application/json |
 *
 * Then HTTP endpoint should eventually return
 *   | endpointAlias | method | path                    | expectedStatus | readTimeout |
 *   | orders-api    | GET    | /orders/ORD-001/status  | 200            | 30          |
 * }</pre>
 *
 * @since 0.1.0
 */
@Slf4j
public class HttpValidationSteps {

    private final SharedHttpResources shared;

    /** PicoContainer constructor injection. */
    public HttpValidationSteps(SharedHttpResources shared) {
        this.shared = shared;
    }

    // -------------------------------------------------------------------------
    // Step definitions
    // -------------------------------------------------------------------------

    /**
     * Asserts the HTTP status code of a previously captured response.
     *
     * <p>DataTable columns: {@code responseAlias}, {@code statusCode}.
     *
     * @param dataTable one-row DataTable
     */
    @Then("expected HTTP response status")
    public void thenExpectedHttpResponseStatus(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);
        ConsumedRecord<String> response = resolveResponse(row);
        String expectedStatus = getRequired(row, "statusCode");

        MatchContext context = MatchContext.builder()
                .matchMethod(RecordMatcherFactory.METHOD_MATCH_ATTRIBUTES)
                .expectedAttributes(Map.of(HttpRequestResponseClient.ATTRIBUTE_STATUS_CODE, expectedStatus))
                .build();

        assertPassed(RecordMatcherFactory.forRaw(RecordMatcherFactory.METHOD_MATCH_ATTRIBUTES), response, context);
    }

    /**
     * Asserts the response body against a local expected file, using the {@code FileRecordMatcher}.
     *
     * <p>DataTable columns: {@code responseAlias}, {@code file}, {@code excludedKeys} (optional, comma-separated).
     *
     * @param dataTable one-row DataTable
     */
    @Then("expected HTTP response body from file")
    public void thenExpectedHttpResponseBodyFromFile(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);
        assertBodyFromFile(row, RecordMatcherFactory.METHOD_MATCH_FILE, "excludedKeys");
    }

    /**
     * Asserts the response body against a local expected XML file, using the {@code XmlRecordMatcher}.
     *
     * <p>DataTable columns: {@code responseAlias}, {@code file}, {@code excludedElements} (optional, comma-separated).
     *
     * @param dataTable one-row DataTable
     */
    @Then("expected HTTP response XML body from file")
    public void thenExpectedHttpResponseXmlBodyFromFile(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);
        assertBodyFromFile(row, RecordMatcherFactory.METHOD_MATCH_XML, "excludedElements");
    }

    /**
     * Asserts a single response header's value.
     *
     * <p>Headers are not modeled as {@link io.github.ktestify.models.ConsumedRecord#getAttributes()}, so this is a
     * direct String comparison rather than a {@code RecordMatcher} invocation. The header name is looked up
     * case-insensitively, per the HTTP specification (and because {@code java.net.http.HttpClient} may normalize the
     * casing of well-known header names).
     *
     * <p>DataTable columns: {@code responseAlias}, {@code header}, {@code value}.
     *
     * @param dataTable one-row DataTable
     */
    @And("HTTP response header should match")
    public void andHttpResponseHeaderShouldMatch(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);
        ConsumedRecord<String> response = resolveResponse(row);
        String header = getRequired(row, "header");
        String expectedValue = getRequired(row, "value");

        String actualValue = response.getHeaders().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(header))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (!expectedValue.equals(actualValue)) {
            throw new AssertionError("Expected HTTP response header '" + header + "' to be '" + expectedValue
                    + "' but was '" + actualValue + "'.");
        }
        log.info("HTTP response header '{}' matched expected value.", header);
    }

    /**
     * Repeatedly calls an endpoint until it returns the expected status code, or the read timeout elapses.
     *
     * <p>Backed by {@link PollingRequestResponseClient} wrapping the scenario's shared
     * {@link HttpRequestResponseClient} - no bespoke polling loop is implemented in this class.
     *
     * <p>DataTable columns: {@code endpointAlias}, {@code method}, {@code path}, {@code expectedStatus},
     * {@code readTimeout} (seconds).
     *
     * @param dataTable one-row DataTable
     */
    @Then("HTTP endpoint should eventually return")
    public void thenHttpEndpointShouldEventuallyReturn(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);

        String endpointAlias = getRequired(row, "endpointAlias");
        String method = getRequired(row, "method");
        String path = getRequired(row, "path");
        String expectedStatus = getRequired(row, "expectedStatus");
        long readTimeoutSeconds = Long.parseLong(getRequired(row, "readTimeout"));

        KtestifyHttpEndpoint endpoint = shared.endpoints.getOrThrow(endpointAlias);
        String url = endpoint.getBaseUrl() + (path.startsWith("/") ? path : "/" + path);

        HttpRequestSpec spec = HttpRequestSpec.builder()
                .method(method.toUpperCase())
                .url(url)
                .headers(
                        endpoint.hasBearerToken()
                                ? Map.of("Authorization", "Bearer " + endpoint.getBearerToken())
                                : Collections.emptyMap())
                .build();

        HttpConsumerContext context = HttpConsumerContext.builder()
                .request(spec)
                .matchMethod(RecordMatcherFactory.METHOD_MATCH_ATTRIBUTES)
                .expectedAttributes(Map.of(HttpRequestResponseClient.ATTRIBUTE_STATUS_CODE, expectedStatus))
                .build();

        PollingRequestResponseClient<HttpRequestSpec, String> pollingClient = new PollingRequestResponseClient<>(
                shared.client,
                records -> !records.isEmpty()
                        && expectedStatus.equals(
                                records.get(0).getAttributes().get(HttpRequestResponseClient.ATTRIBUTE_STATUS_CODE)),
                readTimeoutSeconds * 1000L,
                shared.config.getPollIntervalMs());

        boolean passed = new HttpConsumer(context, pollingClient).call();
        if (!passed) {
            throw new AssertionError("HTTP endpoint '" + endpointAlias + "' (" + method.toUpperCase() + " " + url
                    + ") never returned status " + expectedStatus + " within " + readTimeoutSeconds + "s.");
        }
        log.info(
                "HTTP endpoint '{}' eventually returned status {} within {}s.",
                endpointAlias,
                expectedStatus,
                readTimeoutSeconds);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void assertBodyFromFile(Map<String, String> row, String matchMethod, String excludedColumn) {
        ConsumedRecord<String> response = resolveResponse(row);
        String file = resolve(shared.assetsDirectory, getRequired(row, "file"));
        List<String> excluded = splitComma(row.get(excludedColumn));

        MatchContext context = MatchContext.builder()
                .matchMethod(matchMethod)
                .matchFilePaths(List.of(file))
                .excludedFields(excluded)
                .build();

        assertPassed(RecordMatcherFactory.forRaw(matchMethod), response, context);
    }

    private void assertPassed(RecordMatcher<String> matcher, ConsumedRecord<String> response, MatchContext context) {
        MatchResult result = matcher.match(List.of(response), context);
        if (!result.isPassed()) {
            log.error("HTTP response validation failed:\n{}", result.getDiff());
            throw new AssertionError("HTTP response validation failed: " + result.getDiff());
        }
    }

    private ConsumedRecord<String> resolveResponse(Map<String, String> row) {
        String alias = getRequired(row, "responseAlias");
        return shared.responses.getOrThrow(alias);
    }

    private static List<String> splitComma(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static String getRequired(Map<String, String> row, String col) {
        String v = row.get(col);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Required DataTable column '" + col + "' is missing.");
        }
        return v.trim();
    }

    private static String resolve(String assetsDir, String path) {
        if (assetsDir == null || assetsDir.isBlank() || path == null) return path;
        if (java.nio.file.Path.of(path).isAbsolute()) return path;
        return java.nio.file.Path.of(assetsDir, path).toString();
    }
}
