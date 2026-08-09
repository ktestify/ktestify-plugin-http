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

package io.github.ktestify.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.ktestify.exceptions.ConsumerException;
import io.github.ktestify.exceptions.FetchException;
import io.github.ktestify.http.io.HttpConsumer;
import io.github.ktestify.http.io.HttpConsumerContext;
import io.github.ktestify.http.io.HttpRequestSpec;
import io.github.ktestify.io.core.RequestResponseClient;
import io.github.ktestify.match.RecordMatcherFactory;
import io.github.ktestify.models.ConsumedRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link HttpConsumer} - verifies it correctly wires {@link HttpConsumerContext} into
 * {@link io.github.ktestify.io.core.AbstractSynchronousConsumer}'s buildRequest/buildMatchContext extension points,
 * using a mocked {@link RequestResponseClient} so no real HTTP call is performed.
 */
@DisplayName("HttpConsumer")
class HttpConsumerTest {

    @Mock
    private RequestResponseClient<HttpRequestSpec, String> client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static HttpRequestSpec request() {
        return HttpRequestSpec.builder()
                .method("GET")
                .url("http://localhost/orders")
                .build();
    }

    private static ConsumedRecord<String> record(String statusCode) {
        return ConsumedRecord.<String>builder()
                .source("http://localhost/orders")
                .partition(0)
                .offset(-1L)
                .key("GET")
                .value("{}")
                .timestamp(Instant.now())
                .headers(Map.of())
                .attributes(Map.of("statusCode", statusCode))
                .build();
    }

    @Nested
    @DisplayName("call()")
    class CallTests {

        @Test
        @DisplayName("returns true when the matcher passes")
        void returnsTrueWhenMatcherPasses() {
            when(client.execute(any())).thenReturn(List.of(record("200")));

            HttpConsumerContext context = HttpConsumerContext.builder()
                    .request(request())
                    .matchMethod(RecordMatcherFactory.METHOD_MATCH_ATTRIBUTES)
                    .expectedAttributes(Map.of("statusCode", "200"))
                    .build();

            HttpConsumer consumer = new HttpConsumer(context, client);

            org.junit.jupiter.api.Assertions.assertTrue(consumer.call());
        }

        @Test
        @DisplayName("returns false when the matcher fails")
        void returnsFalseWhenMatcherFails() {
            when(client.execute(any())).thenReturn(List.of(record("500")));

            HttpConsumerContext context = HttpConsumerContext.builder()
                    .request(request())
                    .matchMethod(RecordMatcherFactory.METHOD_MATCH_ATTRIBUTES)
                    .expectedAttributes(Map.of("statusCode", "200"))
                    .build();

            HttpConsumer consumer = new HttpConsumer(context, client);

            org.junit.jupiter.api.Assertions.assertFalse(consumer.call());
        }

        @Test
        @DisplayName("throws ConsumerException wrapping a FetchException from the client")
        void wrapsFetchException() {
            when(client.execute(any())).thenThrow(new FetchException("connection refused"));

            HttpConsumerContext context =
                    HttpConsumerContext.builder().request(request()).build();

            HttpConsumer consumer = new HttpConsumer(context, client);

            org.junit.jupiter.api.Assertions.assertThrows(ConsumerException.class, consumer::call);
        }

        @Test
        @DisplayName("passes with NoOpRecordMatcher when matchMethod is null")
        void passesWithNoOpMatcherWhenMatchMethodNull() {
            when(client.execute(any())).thenReturn(List.of(record("200")));

            HttpConsumerContext context =
                    HttpConsumerContext.builder().request(request()).build();

            HttpConsumer consumer = new HttpConsumer(context, client);

            org.junit.jupiter.api.Assertions.assertTrue(consumer.call());
        }
    }
}
