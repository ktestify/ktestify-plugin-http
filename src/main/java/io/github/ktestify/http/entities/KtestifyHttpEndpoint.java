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

package io.github.ktestify.http.entities;

import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable entity representing an HTTP endpoint registered in a Cucumber scenario.
 *
 * <p>Created by the {@code Given HTTP endpoint} step and stored in
 * {@link io.github.ktestify.http.steps.SharedHttpResources} keyed by both name and alias.
 *
 * <h2>Example</h2>
 *
 * <pre>
 * Given HTTP endpoint
 *   | endpointAlias | baseUrl                    |
 *   | orders-api    | http://localhost:8080/api  |
 * </pre>
 *
 * <p>A bearer token can be attached afterwards via the {@code Given HTTP bearer token} step, which re-registers the
 * endpoint under the same alias with {@link #bearerToken} populated.
 *
 * @since 0.1.0
 */
@Value
@Builder(toBuilder = true)
public class KtestifyHttpEndpoint {

    /** Alias used to reference this endpoint from other steps. Must be non-null. */
    String endpointAlias;

    /** Base URL the endpoint is reachable at (e.g. {@code "http://localhost:8080/api"}). Must be non-null. */
    String baseUrl;

    /** Headers merged into every request sent to this endpoint, on top of the plugin's global default headers. */
    @Builder.Default
    Map<String, String> defaultHeaders = Collections.emptyMap();

    /**
     * Optional bearer token. When non-blank, an {@code Authorization: Bearer <token>} header is added to every request
     * sent to this endpoint, unless already overridden by a request-level header.
     */
    String bearerToken;

    /**
     * Returns {@code true} if a bearer token has been configured for this endpoint.
     *
     * @return {@code true} when {@link #bearerToken} is non-blank
     */
    public boolean hasBearerToken() {
        return bearerToken != null && !bearerToken.isBlank();
    }
}
