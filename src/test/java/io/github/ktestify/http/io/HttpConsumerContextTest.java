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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link HttpConsumerContext}. */
@DisplayName("HttpConsumerContext")
class HttpConsumerContextTest {

    @Test
    @DisplayName("matchFilePaths, excludedFields and expectedAttributes default to empty collections")
    void defaultsToEmptyCollections() {
        HttpRequestSpec request =
                HttpRequestSpec.builder().method("GET").url("http://x").build();
        HttpConsumerContext context =
                HttpConsumerContext.builder().request(request).build();

        assertTrue(context.getMatchFilePaths().isEmpty());
        assertTrue(context.getExcludedFields().isEmpty());
        assertTrue(context.getExpectedAttributes().isEmpty());
        assertNull(context.getMatchMethod());
    }

    @Test
    @DisplayName("builder retains provided values")
    void builderRetainsValues() {
        HttpRequestSpec request =
                HttpRequestSpec.builder().method("GET").url("http://x").build();
        HttpConsumerContext context = HttpConsumerContext.builder()
                .request(request)
                .matchMethod("methodMatchAttributes")
                .matchFilePaths(List.of("expected.json"))
                .excludedFields(List.of("timestamp"))
                .expectedAttributes(Map.of("statusCode", "200"))
                .build();

        assertEquals("methodMatchAttributes", context.getMatchMethod());
        assertEquals(List.of("expected.json"), context.getMatchFilePaths());
        assertEquals(List.of("timestamp"), context.getExcludedFields());
        assertEquals(Map.of("statusCode", "200"), context.getExpectedAttributes());
        assertSame(request, context.getRequest());
    }
}
