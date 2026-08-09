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
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable request description consumed by {@link HttpRequestResponseClient}.
 *
 * <p>This is the {@code Req} type parameter of
 * {@link io.github.ktestify.io.core.RequestResponseClient}{@code <HttpRequestSpec, String>}  -  the sole input required
 * to send one HTTP request. Built by {@link HttpConsumer#buildRequest()} from a per-call
 * context, never constructed directly by step definitions.
 *
 * @since 0.1.0
 * @see HttpRequestResponseClient
 */
@Value
@Builder
public class HttpRequestSpec {

    /** HTTP method (e.g. {@code "GET"}, {@code "POST"}, {@code "PUT"}, {@code "DELETE"}). Must be non-null. */
    String method;

    /** Fully resolved absolute URL to call, including any query string already appended. Must be non-null. */
    String url;

    /** Request headers to send, merged on top of the endpoint's and plugin's default headers. */
    @Builder.Default
    Map<String, String> headers = Collections.emptyMap();

    /**
     * Query parameters to append to {@link #url}. Kept separate from {@link #url} so {@link HttpRequestResponseClient}
     * can perform proper URL-encoding.
     */
    @Builder.Default
    Map<String, String> queryParams = Collections.emptyMap();

    /** Request body, or {@code null} for methods that do not send one (e.g. {@code GET}). */
    String body;
}
