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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared HTTP URL resolution helpers for action and validation steps. */
final class HttpUrlResolver {

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^{}]+)}");

    private HttpUrlResolver() {}

    static ResolvedUrl resolve(String baseUrl, String path, String rawQueryParams) {
        Map<String, String> parsedParams = parseQueryParams(rawQueryParams);
        PathResolution pathResolution = resolvePathTemplate(path, parsedParams);
        String url = buildUrl(baseUrl, pathResolution.resolvedPath());

        Map<String, String> remainingQueryParams = new LinkedHashMap<>(parsedParams);
        pathResolution.usedParameterNames().forEach(remainingQueryParams::remove);

        return new ResolvedUrl(url, remainingQueryParams);
    }

    private static PathResolution resolvePathTemplate(String rawPath, Map<String, String> params) {
        if (rawPath == null || rawPath.isBlank()) {
            return new PathResolution(rawPath, Set.of());
        }

        Matcher matcher = PATH_PARAM_PATTERN.matcher(rawPath);
        StringBuilder resolved = new StringBuilder();
        Set<String> usedNames = new LinkedHashSet<>();
        int lastEnd = 0;

        while (matcher.find()) {
            resolved.append(rawPath, lastEnd, matcher.start());

            String paramName = matcher.group(1).trim();
            String value = params.get(paramName);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "Missing path parameter '" + paramName + "' required by path '" + rawPath + "'.");
            }

            resolved.append(value);
            usedNames.add(paramName);
            lastEnd = matcher.end();
        }

        resolved.append(rawPath.substring(lastEnd));
        String resolvedPath = resolved.toString();

        if (resolvedPath.contains("{") || resolvedPath.contains("}")) {
            throw new IllegalArgumentException(
                    "Malformed path template '" + rawPath + "'. Use placeholders like '{id}'.");
        }

        return new PathResolution(resolvedPath, usedNames);
    }

    private static String buildUrl(String baseUrl, String path) {
        if (path == null || path.isBlank()) {
            return baseUrl;
        }
        boolean baseEndsWithSlash = baseUrl.endsWith("/");
        boolean pathStartsWithSlash = path.startsWith("/");
        if (baseEndsWithSlash && pathStartsWithSlash) {
            return baseUrl + path.substring(1);
        }
        if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    private static Map<String, String> parseQueryParams(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> params = new LinkedHashMap<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(pair -> {
                    int idx = pair.indexOf('=');
                    if (idx < 0) {
                        throw new IllegalArgumentException(
                                "Malformed queryParams entry '" + pair + "', expected 'key=value'.");
                    }

                    String key = pair.substring(0, idx).trim();
                    String value = pair.substring(idx + 1).trim();
                    if (key.isBlank()) {
                        throw new IllegalArgumentException(
                                "Malformed queryParams entry '" + pair + "', key must not be blank.");
                    }
                    params.put(key, value);
                });
        return params;
    }

    record ResolvedUrl(String url, Map<String, String> queryParams) {}

    private record PathResolution(String resolvedPath, Set<String> usedParameterNames) {}
}

