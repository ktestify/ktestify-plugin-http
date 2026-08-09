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

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link HttpRequestSpec}. */
@DisplayName("HttpRequestSpec")
class HttpRequestSpecTest {

    @Test
    @DisplayName("headers and queryParams default to empty maps")
    void defaultsToEmptyMaps() {
        HttpRequestSpec spec = HttpRequestSpec.builder()
                .method("GET")
                .url("http://localhost/x")
                .build();

        assertTrue(spec.getHeaders().isEmpty());
        assertTrue(spec.getQueryParams().isEmpty());
        assertNull(spec.getBody());
    }

    @Test
    @DisplayName("builder retains provided values")
    void builderRetainsValues() {
        HttpRequestSpec spec = HttpRequestSpec.builder()
                .method("POST")
                .url("http://localhost/orders")
                .headers(Map.of("Content-Type", "application/json"))
                .queryParams(Map.of("id", "ORD-001"))
                .body("{}")
                .build();

        assertEquals("POST", spec.getMethod());
        assertEquals("http://localhost/orders", spec.getUrl());
        assertEquals("application/json", spec.getHeaders().get("Content-Type"));
        assertEquals("ORD-001", spec.getQueryParams().get("id"));
        assertEquals("{}", spec.getBody());
    }
}
