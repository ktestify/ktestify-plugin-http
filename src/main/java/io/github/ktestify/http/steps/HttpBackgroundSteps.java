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
import io.cucumber.java.en.Given;
import io.github.ktestify.http.entities.KtestifyHttpEndpoint;
import io.github.ktestify.io.inputs.DynamicVariableProcessor;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Cucumber {@code @Given} step definitions for HTTP setup.
 *
 * <p>These steps register endpoints into {@link SharedHttpResources} so they can be referenced by alias from action and
 * validation steps.
 *
 * <h2>Example usage</h2>
 *
 * <pre>{@code
 * Given HTTP endpoint
 *   | endpointAlias | baseUrl                    |
 *   | orders-api    | http://localhost:8080/api  |
 *
 * Given HTTP bearer token
 *   | endpointAlias | token              |
 *   | orders-api    | {{ENV:API_TOKEN}}  |
 * }</pre>
 *
 * <p>{@code {{ENV:API_TOKEN}}} and any other registered dynamic variable is resolved via
 * {@link DynamicVariableProcessor}, ktestify-core's existing templating system  -  no new templating is introduced by
 * this plugin.
 *
 * @since 0.1.0
 */
@Slf4j
public class HttpBackgroundSteps {

    private final SharedHttpResources shared;
    private final DynamicVariableProcessor dynamicVariableProcessor = new DynamicVariableProcessor();

    /** PicoContainer constructor injection. */
    public HttpBackgroundSteps(SharedHttpResources shared) {
        this.shared = shared;
    }

    // -------------------------------------------------------------------------
    // Step definitions
    // -------------------------------------------------------------------------

    /**
     * Registers one HTTP endpoint.
     *
     * <p>DataTable columns:
     *
     * <table>
     *   <tr><th>Column</th><th>Required</th><th>Description</th></tr>
     *   <tr><td>endpointAlias</td><td>yes</td><td>Alias used in subsequent steps</td></tr>
     *   <tr><td>baseUrl</td><td>yes</td><td>Base URL the endpoint is reachable at</td></tr>
     * </table>
     *
     * @param dataTable one-row DataTable defining the endpoint
     */
    @Given("HTTP endpoint")
    public void givenHttpEndpoint(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);

        String endpointAlias = getRequired(row, "endpointAlias");
        String baseUrl = getRequired(row, "baseUrl");

        KtestifyHttpEndpoint endpoint = KtestifyHttpEndpoint.builder()
                .endpointAlias(endpointAlias)
                .baseUrl(baseUrl)
                .build();

        shared.endpoints.register(endpointAlias, endpoint);
        log.info("Registered HTTP endpoint '{}' → '{}'.", endpointAlias, baseUrl);
    }

    /**
     * Registers multiple HTTP endpoints in a single step. Each row follows the same column convention as
     * {@link #givenHttpEndpoint(DataTable)}.
     *
     * @param dataTable multi-row DataTable defining each endpoint
     */
    @Given("HTTP endpoints")
    public void givenHttpEndpoints(DataTable dataTable) {
        for (Map<String, String> row : dataTable.asMaps()) {
            String endpointAlias = getRequired(row, "endpointAlias");
            String baseUrl = getRequired(row, "baseUrl");

            KtestifyHttpEndpoint endpoint = KtestifyHttpEndpoint.builder()
                    .endpointAlias(endpointAlias)
                    .baseUrl(baseUrl)
                    .build();

            shared.endpoints.register(endpointAlias, endpoint);
            log.info("Registered HTTP endpoint '{}' → '{}'.", endpointAlias, baseUrl);
        }
    }

    /**
     * Attaches a bearer token to a previously registered endpoint.
     *
     * <p>The {@code token} value may contain a dynamic variable (e.g. {@code {{ENV:API_TOKEN}}}), resolved via
     * {@link DynamicVariableProcessor}. Every request sent to this endpoint afterwards carries an {@code Authorization:
     * Bearer <token>} header.
     *
     * <p>DataTable columns:
     *
     * <table>
     *   <tr><th>Column</th><th>Required</th><th>Description</th></tr>
     *   <tr><td>endpointAlias</td><td>yes</td><td>Alias of a previously registered endpoint</td></tr>
     *   <tr><td>token</td><td>yes</td><td>Bearer token value, may contain a dynamic variable</td></tr>
     * </table>
     *
     * @param dataTable one-row DataTable defining the token
     */
    @Given("HTTP bearer token")
    public void givenHttpBearerToken(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);

        String endpointAlias = getRequired(row, "endpointAlias");
        String token = getRequired(row, "token");
        String resolvedToken = dynamicVariableProcessor.process(token);

        KtestifyHttpEndpoint existing = shared.endpoints.getOrThrow(endpointAlias);
        KtestifyHttpEndpoint updated =
                existing.toBuilder().bearerToken(resolvedToken).build();

        shared.endpoints.register(endpointAlias, updated);
        log.info("Bearer token configured for HTTP endpoint '{}'.", endpointAlias);
    }

    /**
     * Overrides the assets directory for the current scenario. Useful when this plugin's file-based steps need a
     * different base path than the global configuration.
     *
     * <p>DataTable columns:
     *
     * <table>
     *   <tr><th>Column</th><th>Required</th><th>Description</th></tr>
     *   <tr><td>absolutePath</td><td>yes</td><td>Absolute path to the assets directory</td></tr>
     * </table>
     *
     * @param dataTable one-row DataTable with column {@code absolutePath}
     */
    @Given("HTTP assets directory")
    public void givenHttpAssetsDirectory(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);
        String path = row.get("absolutePath");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "DataTable column 'absolutePath' is required for 'Given HTTP assets directory'.");
        }
        shared.assetsDirectory = path;
        log.info("HTTP assets directory set to '{}'.", path);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String getRequired(Map<String, String> row, String col) {
        String v = row.get(col);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Required DataTable column '" + col + "' is missing.");
        }
        return v.trim();
    }
}
