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

import io.github.ktestify.io.core.AbstractSynchronousConsumer;
import io.github.ktestify.io.core.RequestResponseClient;
import io.github.ktestify.match.MatchContext;
import io.github.ktestify.match.RecordMatcher;
import io.github.ktestify.match.RecordMatcherFactory;
import java.util.Collections;

/**
 * Orchestration-layer consumer for HTTP.
 *
 * <p>Follows the same three-layer separation as every other ktestify transport:
 *
 * <ol>
 *   <li><b>Transport</b>  -  {@link io.github.ktestify.http.io.HttpRequestResponseClient}: sends the request and receives
 *       the response.
 *   <li><b>Orchestration</b>  -  this class: wires request → execute → match → result, inherited entirely from
 *       {@link AbstractSynchronousConsumer}.
 *   <li><b>Assertion</b>  -  {@link RecordMatcher} implementation selected by {@code matchMethod}, reused unchanged from
 *       {@code ktestify-core} (no HTTP-specific matcher code).
 * </ol>
 *
 * @since 0.1.0
 * @see io.github.ktestify.http.io.HttpRequestResponseClient
 * @see io.github.ktestify.http.steps.SharedHttpResources
 */
public class HttpConsumer extends AbstractSynchronousConsumer<HttpRequestSpec, String> {

    private final HttpConsumerContext context;

    /**
     * Creates a consumer that resolves its matcher from {@code context.getMatchMethod()}.
     *
     * @param context per-call configuration (request, match method, expected file paths / attributes)
     * @param client the shared HTTP transport (see {@link io.github.ktestify.http.steps.SharedHttpResources})
     */
    public HttpConsumer(HttpConsumerContext context, RequestResponseClient<HttpRequestSpec, String> client) {
        super(Collections.emptyMap(), client, RecordMatcherFactory.forRaw(context.getMatchMethod()));
        this.context = context;
    }

    /**
     * Returns the pre-built request carried by this consumer's {@link HttpConsumerContext}.
     *
     * @return the request to send
     */
    @Override
    protected HttpRequestSpec buildRequest() {
        return context.getRequest();
    }

    /**
     * Builds the {@link MatchContext} from this consumer's {@link HttpConsumerContext}, mapping {@code matchMethod},
     * {@code matchFilePaths}, {@code excludedFields}, and {@code expectedAttributes} exactly like
     * {@code AbstractKafkaConsumer.buildMatchContext()} does for Kafka.
     *
     * @return the match context for this invocation
     */
    @Override
    protected MatchContext buildMatchContext() {
        return MatchContext.builder()
                .matchMethod(context.getMatchMethod())
                .matchFilePaths(context.getMatchFilePaths())
                .excludedFields(context.getExcludedFields())
                .expectedAttributes(context.getExpectedAttributes())
                .strictMatching(false)
                .build();
    }
}
