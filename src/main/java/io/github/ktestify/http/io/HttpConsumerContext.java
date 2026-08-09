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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable context object that configures an {@link HttpConsumerContext}-driven call.
 *
 * <p>Mirrors the design of {@code AzureBlobConsumerContext} in {@code ktestify-plugin-azureblob}: a pure value object
 * built by the step layer ({@code io.github.ktestify.http.steps}) and consumed by
 * {@link HttpConsumer}.
 *
 * @since 0.1.0
 * @see HttpConsumer
 */
@Value
@Builder
public class HttpConsumerContext {

    /** The fully resolved request to send. Must be non-null. */
    HttpRequestSpec request;

    /**
     * Match method  -  same constants as {@code RecordMatcherFactory.METHOD_*} (e.g. {@code "methodMatchFile"},
     * {@code "methodMatchAttributes"}). May be {@code null} to skip content comparison.
     */
    String matchMethod;

    /**
     * Ordered list of expected-content file paths used by the matcher. Single-record matchers use
     * {@code matchFilePaths.get(0)}. Defaults to an empty list.
     */
    @Builder.Default
    List<String> matchFilePaths = Collections.emptyList();

    /** Field names (or XML element names) to exclude during comparison. Defaults to an empty list. */
    @Builder.Default
    List<String> excludedFields = Collections.emptyList();

    /**
     * Expected transport-attribute key/value pairs (status code, elapsed time, …), used only when {@link #matchMethod}
     * is {@code RecordMatcherFactory.METHOD_MATCH_ATTRIBUTES}. Defaults to an empty map.
     */
    @Builder.Default
    Map<String, String> expectedAttributes = Collections.emptyMap();
}
