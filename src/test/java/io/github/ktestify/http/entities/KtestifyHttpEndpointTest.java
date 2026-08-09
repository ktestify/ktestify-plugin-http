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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

/** Unit tests for {@link KtestifyHttpEndpoint}. */
@DisplayName("KtestifyHttpEndpoint")
class KtestifyHttpEndpointTest {

    @Test
    @DisplayName("hasBearerToken() is false when bearerToken is null")
    void hasBearerTokenFalseWhenNull() {
        KtestifyHttpEndpoint endpoint = KtestifyHttpEndpoint.builder()
                .endpointAlias("a")
                .baseUrl("http://x")
                .build();
        assertFalse(endpoint.hasBearerToken());
    }

    @Test
    @DisplayName("hasBearerToken() is false when bearerToken is blank")
    void hasBearerTokenFalseWhenBlank() {
        KtestifyHttpEndpoint endpoint = KtestifyHttpEndpoint.builder()
                .endpointAlias("a")
                .baseUrl("http://x")
                .bearerToken("   ")
                .build();
        assertFalse(endpoint.hasBearerToken());
    }

    @Test
    @DisplayName("hasBearerToken() is true when bearerToken is set")
    void hasBearerTokenTrueWhenSet() {
        KtestifyHttpEndpoint endpoint = KtestifyHttpEndpoint.builder()
                .endpointAlias("a")
                .baseUrl("http://x")
                .bearerToken("abc123")
                .build();
        assertTrue(endpoint.hasBearerToken());
    }

    @Test
    @DisplayName("defaultHeaders defaults to an empty map")
    void defaultHeadersDefaultsEmpty() {
        KtestifyHttpEndpoint endpoint = KtestifyHttpEndpoint.builder()
                .endpointAlias("a")
                .baseUrl("http://x")
                .build();
        assertTrue(endpoint.getDefaultHeaders().isEmpty());
    }

    @Test
    @DisplayName("toBuilder() preserves existing fields while allowing overrides")
    void toBuilderPreservesFields() {
        KtestifyHttpEndpoint endpoint = KtestifyHttpEndpoint.builder()
                .endpointAlias("a")
                .baseUrl("http://x")
                .build();

        KtestifyHttpEndpoint updated = endpoint.toBuilder().bearerToken("tok").build();

        assertEquals("a", updated.getEndpointAlias());
        assertEquals("http://x", updated.getBaseUrl());
        assertEquals("tok", updated.getBearerToken());
    }
}
