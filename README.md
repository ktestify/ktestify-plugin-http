<p align="center">
  <img src="https://raw.githubusercontent.com/ktestify/.github/refs/heads/main/profile/assets/png/ktestify-banner-2x.png" alt="ktestify-plugin-http" width="100%"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/build-passing-6EE7B7?style=flat-square&labelColor=0C1018&color=6EE7B7" alt="build passing"/>
  <img src="https://img.shields.io/badge/license-Apache%202.0-6EE7B7?style=flat-square&labelColor=0C1018&color=6EE7B7" alt="license"/>
  <img src="https://img.shields.io/badge/java-25-2DD4BF?style=flat-square&labelColor=0C1018&color=2DD4BF" alt="java 25"/>
  <img src="https://img.shields.io/badge/version-0.1.0--SNAPSHOT-6EE7B7?style=flat-square&labelColor=0C1018&color=6EE7B7" alt="version"/>
</p>

<br/>

**ktestify-plugin-http** is a [ktestify](https://github.com/ktestify) plugin that adds synchronous **HTTP request/response** transport support. It implements the `KtestifyPlugin` SPI from [ktestify-core](https://github.com/ktestify/ktestify-core) and ships ready-to-use Cucumber step definitions for calling HTTP endpoints and asserting their responses, side by side with your Kafka integration test scenarios.

Built entirely on top of `ktestify-core`'s `RequestResponseClient` / `AbstractSynchronousConsumer` / `AttributeRecordMatcher` contracts (introduced for synchronous transports), using nothing more than the JDK's built-in `java.net.http.HttpClient`. No new HTTP client dependency is added.

Drop the JAR into your `ktestify-cucumber` setup and the steps are automatically discovered, no code changes required.

---

## Installation

```xml
<dependency>
  <groupId>io.github.ktestify</groupId>
  <artifactId>ktestify-plugin-http</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

### With ktestify-cucumber (fat JAR / Docker)

Drop the plugin JAR into the `/workspace/plugins` mount and ktestify-cucumber will load it automatically via `ServiceLoader` at startup:

```bash
docker run --rm \
  -v $(pwd)/features:/workspace/features \
  -v $(pwd)/assets:/workspace/assets \
  -v $(pwd)/plugins:/workspace/plugins \   # ← drop ktestify-plugin-http-*.jar here
  ghcr.io/ktestify/ktestify-cucumber:latest \
  /workspace/features
```

---

## What It Adds

### Step Definitions

```gherkin
Given HTTP endpoint
  | endpointAlias | baseUrl                    |
  | orders-api    | http://localhost:8080/api  |

Given HTTP bearer token
  | endpointAlias | token              |
  | orders-api    | {{ENV:API_TOKEN}}  |

When HTTP request is sent
  | endpointAlias | method | path             | file        | responseAlias |
  | orders-api    | POST   | /orders/validate | order.json  | validate-resp |

Then expected HTTP response status
  | responseAlias | statusCode |
  | validate-resp | 200        |

Then expected HTTP response body from file
  | responseAlias | file          | excludedKeys |
  | validate-resp | expected.json | timestamp,id |

And HTTP response header should match
  | responseAlias | header       | value            |
  | validate-resp | Content-Type | application/json |

Then HTTP endpoint should eventually return
  | endpointAlias | method | path                    | expectedStatus | readTimeout |
  | orders-api    | GET    | /orders/ORD-001/status  | 200            | 30          |
```

### Full Scenario Example

```gherkin
Feature: Order validation API

  Background:
    Given HTTP endpoint
      | endpointAlias | baseUrl                    |
      | orders-api    | http://localhost:8080/api  |
    Given HTTP bearer token
      | endpointAlias | token              |
      | orders-api    | {{ENV:API_TOKEN}}  |
    Given HTTP assets directory
      | absolutePath                    |
      | src/test/resources/data/orders  |

  Scenario: Validating an order returns 200 with the echoed body
    When HTTP request is sent
      | endpointAlias | method | path             | file       | responseAlias |
      | orders-api    | POST   | /orders/validate | order.json | validate-resp |
    Then expected HTTP response status
      | responseAlias | statusCode |
      | validate-resp | 200        |
    Then expected HTTP response body from file
      | responseAlias | file          |
      | validate-resp | expected.json |
```

---

## Configuration

The plugin reads its settings from the `ktestify.plugins.http` HOCON block. All values can be overridden via environment variables.

```hocon
ktestify.plugins.http {
  # Maximum time to wait while establishing the TCP/TLS connection.
  connect-timeout = 10s
  connect-timeout = ${?KTESTIFY_HTTP_CONNECT_TIMEOUT}

  # Maximum time to wait for the full response to be received.
  read-timeout = 30s
  read-timeout = ${?KTESTIFY_HTTP_READ_TIMEOUT}

  # Interval between attempts for the "eventually consistent" polling step.
  poll-interval = 500ms
  poll-interval = ${?KTESTIFY_HTTP_POLL_INTERVAL}

  # Whether the underlying HttpClient automatically follows HTTP redirects.
  follow-redirects = true
  follow-redirects = ${?KTESTIFY_HTTP_FOLLOW_REDIRECTS}

  # Headers merged into every request, overridden by per-endpoint and per-request headers.
  default-headers { }

  tls {
    # Explicit opt-in only. Disables TLS certificate validation, local/dev endpoints only.
    trust-all = false
    trust-all = ${?KTESTIFY_HTTP_TLS_TRUST_ALL}
  }
}
```

---

## Architecture

This plugin implements the `KtestifyPlugin` SPI:

```java
public final class HttpPlugin implements KtestifyPlugin {
    @Override public String getId()          { return "http"; }
    @Override public String getGluePackage() { return "io.github.ktestify.http.steps"; }

    @Override
    public void initialize(PluginContext ctx) {
        // reads ktestify.plugins.http from ctx.getConfig()
    }
}
```

It is discovered automatically by `ServiceLoader`, the `META-INF/services/io.github.ktestify.plugin.KtestifyPlugin` descriptor is included in the JAR.

### Package layout

```
io.github.ktestify.http
├── HttpPlugin.java                    # KtestifyPlugin SPI entry point
├── HttpConsumer.java                  # extends AbstractSynchronousConsumer<HttpRequestSpec, String>
├── config/HttpConfig.java             # typed ktestify.plugins.http config
├── entities/KtestifyHttpEndpoint.java # registered endpoint (alias, baseUrl, bearer token)
├── io/HttpRequestSpec.java            # immutable request description
├── io/HttpRequestResponseClient.java  # RequestResponseClient<HttpRequestSpec, String> over HttpClient
├── io/HttpConsumerContext.java        # per-call context (request, matchMethod, expected*)
└── steps/
    ├── HttpBackgroundSteps.java       # @Given  -  endpoint registration, bearer token
    ├── HttpActionSteps.java           # @When  -  send request
    ├── HttpValidationSteps.java       # @Then/@And  -  status/body/header assertions, polling
    └── SharedHttpResources.java       # PicoContainer-scoped shared state
```

### Reused, unchanged `ktestify-core` matchers

Body assertions reuse `FileRecordMatcher` and `XmlRecordMatcher` unchanged. Status code assertions and the polling
step reuse `AttributeRecordMatcher` unchanged. No new matcher classes exist in this plugin.

---

## Related

- **[ktestify-core](https://github.com/ktestify/ktestify-core)**  -  the foundation library and plugin SPI
- **[ktestify-cucumber](https://github.com/ktestify/ktestify-cucumber)**  -  the BDD runner this plugin extends
- **[docs.ktestify.xyz](https://docs.ktestify.xyz)**  -  full documentation and configuration reference

---

## Contributing

Contributions are welcome. Please read the contributing guide before opening a pull request.

1. Fork the repository
2. Create a feature branch, `git checkout -b feat/my-feature`
3. Commit with [Conventional Commits](https://www.conventionalcommits.org/), `git commit -m "feat: add my feature"`
4. Push and open a Pull Request against `main`

---

## License

ktestify-plugin-http is licensed under the [Apache License 2.0](LICENSE).

---

<p align="center">
  <img src="https://raw.githubusercontent.com/ktestify/.github/refs/heads/main/profile/assets/png/ktestify-logo-128.png" width="32" height="32" alt="KTestify"/>
  <br/>
  <sub>Assert the stream. Own the pipeline.</sub>
</p>

