package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.ImposterDefinition;
import io.github.etacassiopeia.rift.model.ImposterDefinitions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.and;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.body;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.contains;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.created;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.deepEquals;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.endsWith;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.exists;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.header;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.imposter;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.json;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.matches;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.method;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.noContent;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.notFound;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.not;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.ok;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.okJson;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onGet;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onPost;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onRequest;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.path;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.startsWith;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate for issue #3: every corpus fixture (the same six fixtures {@code
 * WireModelRoundTripTest} exercises for the wire model itself) must be buildable using <em>only</em>
 * the {@link RiftDsl} static API — never {@code ImposterDefinitions.fromJson}/{@code Stub.fromJson} — and the
 * built model must serialize to a JSON tree semantically equal to the fixture. Parsing the fixture
 * is used only to obtain the expected tree for the comparison, never to build the model under test.
 *
 * <p>{@link RiftDsl#equals(String)} and its {@code JsonValue} overload are always called fully
 * qualified ({@code RiftDsl.equals(...)}) below, never via the static import: an unqualified
 * {@code equals(...)} call always resolves to the inherited {@link Object#equals(Object)} instead
 * (a String/JsonValue argument is applicable to {@code Object}, and Java resolves a simple method
 * name against inherited members before ever considering static imports, in every context, static
 * or not) — so {@code equals} is the one matcher name that cannot be used unqualified.
 */
class CorpusExpressibilityTest {

    @Test
    void basicApi() {
        assertFixtureExpressible("basic-api.json", buildBasicApi());
    }

    @Test
    void authenticationApi() {
        assertFixtureExpressible("authentication-api.json", buildAuthenticationApi());
    }

    @Test
    void errorTesting() {
        assertFixtureExpressible("error-testing.json", buildErrorTesting());
    }

    @Test
    void featureFlagsApi() {
        assertFixtureExpressible("feature-flags-api.json", buildFeatureFlagsApi());
    }

    @Test
    void latencyTesting() {
        assertFixtureExpressible("latency-testing.json", buildLatencyTesting());
    }

    @Test
    void taskManagementApi() {
        assertFixtureExpressible("task-management-api.json", buildTaskManagementApi());
    }

    private static ImposterDefinition buildBasicApi() {
        return imposter("Basic REST API")
                .port(4545)
                .protocol("http")
                .stub(
                        onGet("/health")
                                .willReturn(ok().withTextBody("OK")),
                        onGet("/api/users")
                                .willReturn(okJson("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]")),
                        onRequest()
                                .withPredicate(and(method(RiftDsl.equals("GET")), path(matches("/api/users/\\d+"))))
                                .willReturn(okJson("{\"id\":1,\"name\":\"Alice\",\"email\":\"alice@example.com\"}")),
                        onPost("/api/users")
                                .willReturn(created()
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"id\":999,\"message\":\"Created\"}")))
                .build();
    }

    private static ImposterDefinition buildAuthenticationApi() {
        return imposter("Authentication API")
                .port(4547)
                .protocol("http")
                .allowCors()
                .stub(
                        onRequest()
                                .withPath(RiftDsl.equals("/auth/login"))
                                .withMethod(deepEquals("POST"))
                                .withBody(RiftDsl.equals(json("{\"username\":\"admin\",\"password\":\"secret123\"}")))
                                .scenario("Auth-Login-Success")
                                .willReturn(okJson("""
                                        {
                                          "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJhZG1pbiJ9.mock",
                                          "expiresIn": 3600,
                                          "tokenType": "Bearer",
                                          "user": {"id": "user-001", "username": "admin", "role": "admin"}
                                        }
                                        """)),
                        onRequest()
                                .withPath(RiftDsl.equals("/auth/login"))
                                .withMethod(deepEquals("POST"))
                                .withBody(contains(json("{\"username\":\"admin\"}")))
                                .withPredicate(not(body(RiftDsl.equals(json("{\"password\":\"secret123\"}")))))
                                .scenario("Auth-Login-InvalidCredentials")
                                .willReturn(status(401)
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"error\":\"Invalid credentials\",\"code\":\"AUTH_INVALID_CREDENTIALS\"}")),
                        onRequest()
                                .withPath(RiftDsl.equals("/auth/login"))
                                .withMethod(deepEquals("POST"))
                                .withPredicate(not(body(contains(json("{\"username\":\"admin\"}")))))
                                .scenario("Auth-Login-UserNotFound")
                                .willReturn(status(401)
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"error\":\"User not found\",\"code\":\"AUTH_USER_NOT_FOUND\"}")),
                        onRequest()
                                .withPath(RiftDsl.equals("/auth/validate"))
                                .withMethod(deepEquals("GET"))
                                .withPredicate(header("Authorization", exists()))
                                .withPredicate(header("Authorization", startsWith("Bearer ")))
                                .scenario("Auth-ValidateToken-Success")
                                .willReturn(okJson("""
                                        {"valid": true, "user": {"id": "user-001", "username": "admin", "role": "admin"}}
                                        """)),
                        onRequest()
                                .withPath(RiftDsl.equals("/auth/validate"))
                                .withMethod(deepEquals("GET"))
                                .withPredicate(not(header("Authorization", exists())))
                                .scenario("Auth-ValidateToken-Missing")
                                .willReturn(status(401)
                                        .withHeader("Content-Type", "application/json")
                                        .withHeader("WWW-Authenticate", "Bearer realm=\"api\"")
                                        .withJsonBody("{\"error\":\"Missing authorization header\",\"code\":\"AUTH_MISSING_TOKEN\"}")),
                        onRequest()
                                .withPath(RiftDsl.equals("/auth/logout"))
                                .withMethod(deepEquals("POST"))
                                .scenario("Auth-Logout-Success")
                                .willReturn(okJson("{\"message\":\"Logged out successfully\"}")))
                .build();
    }

    private static ImposterDefinition buildErrorTesting() {
        return imposter("Error Testing")
                .port(4545)
                .protocol("http")
                .stub(
                        onRequest().withPath(RiftDsl.equals("/success"))
                                .willReturn(okJson("{\"status\":\"ok\"}")),
                        onRequest().withPath(RiftDsl.equals("/error/400"))
                                .willReturn(status(400)
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"error\":\"Bad Request\",\"code\":\"INVALID_INPUT\"}")),
                        onRequest().withPath(RiftDsl.equals("/error/401"))
                                .willReturn(status(401)
                                        .withHeader("Content-Type", "application/json")
                                        .withHeader("WWW-Authenticate", "Bearer realm=\"api\"")
                                        .withJsonBody("{\"error\":\"Unauthorized\"}")),
                        onRequest().withPath(RiftDsl.equals("/error/403"))
                                .willReturn(status(403)
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"error\":\"Forbidden\"}")),
                        onRequest().withPath(RiftDsl.equals("/error/404"))
                                .willReturn(notFound()
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"error\":\"Not Found\"}")),
                        onRequest().withPath(RiftDsl.equals("/error/429"))
                                .willReturn(status(429)
                                        .withHeader("Content-Type", "application/json")
                                        .withHeader("Retry-After", "60")
                                        .withJsonBody("{\"error\":\"Too Many Requests\",\"retry_after\":60}")),
                        onRequest().withPath(RiftDsl.equals("/error/500"))
                                .willReturn(status(500)
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"error\":\"Internal Server Error\"}")),
                        onRequest().withPath(RiftDsl.equals("/error/502"))
                                .willReturn(status(502)
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"error\":\"Bad Gateway\"}")),
                        onRequest().withPath(RiftDsl.equals("/error/503"))
                                .willReturn(status(503)
                                        .withHeader("Content-Type", "application/json")
                                        .withHeader("Retry-After", "30")
                                        .withJsonBody("{\"error\":\"Service Unavailable\"}")),
                        onRequest().withPath(RiftDsl.equals("/error/504"))
                                .willReturn(status(504)
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"error\":\"Gateway Timeout\"}")))
                .build();
    }

    private static ImposterDefinition buildFeatureFlagsApi() {
        return imposter("Feature Flags API")
                .port(4546)
                .protocol("http")
                .allowCors()
                .stub(
                        onRequest()
                                .withPath(endsWith("/features/DARK_MODE"))
                                .withMethod(deepEquals("GET"))
                                .scenario("FeatureFlags-DarkMode-Enabled")
                                .willReturn(okJson("""
                                        {"featureId": "DARK_MODE", "featureName": "Dark Mode", "description": "Enable dark mode UI theme", "isEnabled": true}
                                        """)),
                        onRequest()
                                .withPath(endsWith("/features/BETA_FEATURE"))
                                .withMethod(deepEquals("GET"))
                                .scenario("FeatureFlags-BetaFeature-Disabled")
                                .willReturn(okJson("""
                                        {"featureId": "BETA_FEATURE", "featureName": "Beta Feature", "description": "New experimental feature", "isEnabled": false}
                                        """)),
                        onRequest()
                                .withPath(matches("/features/UNKNOWN_.*"))
                                .withMethod(deepEquals("GET"))
                                .scenario("FeatureFlags-NotFound")
                                .willReturn(notFound()
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"error\":\"Feature flag not found\"}")),
                        onRequest()
                                .withPath(RiftDsl.equals("/features"))
                                .withMethod(deepEquals("GET"))
                                .scenario("FeatureFlags-ListAll")
                                .willReturn(okJson("""
                                        {"features": [
                                          {"featureId": "DARK_MODE", "isEnabled": true},
                                          {"featureId": "BETA_FEATURE", "isEnabled": false},
                                          {"featureId": "NEW_DASHBOARD", "isEnabled": true}
                                        ]}
                                        """)))
                .build();
    }

    private static ImposterDefinition buildLatencyTesting() {
        return imposter("Latency Testing")
                .port(4545)
                .protocol("http")
                .stub(
                        onRequest().withPath(RiftDsl.equals("/fast"))
                                .willReturn(ok().withTextBody("Fast response")),
                        onRequest().withPath(RiftDsl.equals("/slow-100ms"))
                                .willReturn(ok().withTextBody("100ms delay").waitMs(100)),
                        onRequest().withPath(RiftDsl.equals("/slow-500ms"))
                                .willReturn(ok().withTextBody("500ms delay").waitMs(500)),
                        onRequest().withPath(RiftDsl.equals("/slow-2s"))
                                .willReturn(ok().withTextBody("2 second delay").waitMs(2000)),
                        onRequest().withPath(RiftDsl.equals("/timeout"))
                                .willReturn(ok().withTextBody("Timeout test (35s)").waitMs(35000)),
                        onRequest().withPath(RiftDsl.equals("/random-latency"))
                                .willReturn(ok().withTextBody("Random latency 100-1000ms")
                                        .waitInject("function() { return Math.floor(Math.random() * 900) + 100; }")))
                .build();
    }

    private static ImposterDefinition buildTaskManagementApi() {
        return imposter("Task Management API")
                .port(4545)
                .protocol("http")
                .allowCors()
                .record()
                .stub(
                        onRequest()
                                .withPath(endsWith("/tasks"))
                                .withMethod(deepEquals("GET"))
                                .scenario("TaskAPI-GetTasks-Success")
                                .willReturn(okJson("""
                                        {"count": 3, "tasks": [
                                          {"taskId": "task-001", "name": "Review PR", "status": "OPEN", "priority": "HIGH"},
                                          {"taskId": "task-002", "name": "Update docs", "status": "IN_PROGRESS", "priority": "MEDIUM"},
                                          {"taskId": "task-003", "name": "Fix bug", "status": "CLOSED", "priority": "LOW"}
                                        ]}
                                        """)),
                        onRequest()
                                .withPath(endsWith("/tasks"))
                                .withQuery("status", "OPEN")
                                .withMethod(deepEquals("GET"))
                                .scenario("TaskAPI-GetTasks-FilterByStatus")
                                .willReturn(okJson("""
                                        {"count": 1, "tasks": [
                                          {"taskId": "task-001", "name": "Review PR", "status": "OPEN", "priority": "HIGH"}
                                        ]}
                                        """)),
                        onRequest()
                                .withPath(matches("/tasks/task-\\d+"))
                                .withMethod(deepEquals("GET"))
                                .scenario("TaskAPI-GetTaskById-Success")
                                .willReturn(okJson("""
                                        {
                                          "taskId": "task-001",
                                          "name": "Review PR",
                                          "description": "Review the latest pull request for the API changes",
                                          "status": "OPEN",
                                          "priority": "HIGH",
                                          "assignee": "alice@example.com",
                                          "createdAt": "2024-01-15T10:00:00Z",
                                          "updatedAt": "2024-01-15T14:30:00Z"
                                        }
                                        """)),
                        onRequest()
                                .withPath(matches("/tasks/task-999"))
                                .withMethod(deepEquals("GET"))
                                .scenario("TaskAPI-GetTaskById-NotFound")
                                .willReturn(notFound()
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"error\":\"Task not found\",\"code\":\"TASK_NOT_FOUND\"}")),
                        onRequest()
                                .withPath(endsWith("/tasks"))
                                .withMethod(deepEquals("POST"))
                                .withPredicate(body(exists()).jsonPath("$.name"))
                                .scenario("TaskAPI-CreateTask-Success")
                                .willReturn(created()
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("{\"taskId\":\"task-new-001\",\"message\":\"Task created successfully\"}")),
                        onRequest()
                                .withPath(endsWith("/tasks"))
                                .withMethod(deepEquals("POST"))
                                .withPredicate(not(body(exists()).jsonPath("$.name")))
                                .scenario("TaskAPI-CreateTask-ValidationError")
                                .willReturn(status(400)
                                        .withHeader("Content-Type", "application/json")
                                        .withJsonBody("""
                                                {
                                                  "error": "Validation failed",
                                                  "code": "VALIDATION_ERROR",
                                                  "details": [{"field": "name", "message": "Name is required"}]
                                                }
                                                """)),
                        onRequest()
                                .withPath(matches("/tasks/task-\\d+"))
                                .withMethod(deepEquals("PUT"))
                                .scenario("TaskAPI-UpdateTask-Success")
                                .willReturn(okJson("{\"message\":\"Task updated successfully\"}")),
                        onRequest()
                                .withPath(matches("/tasks/task-\\d+"))
                                .withMethod(deepEquals("DELETE"))
                                .scenario("TaskAPI-DeleteTask-Success")
                                .willReturn(noContent()))
                .build();
    }

    private static void assertFixtureExpressible(String fixtureName, ImposterDefinition built) {
        String fixtureText = readFixture(fixtureName);
        JsonValue expected = JsonValue.parse(fixtureText);
        JsonValue actual = JsonValue.parse(new ImposterDefinitions(List.of(built)).toJson());
        assertTrue(
                JsonValue.semanticEquals(expected, actual),
                () -> fixtureName + ": DSL-built imposter does not match the fixture.\n  expected: "
                        + expected.toJson() + "\n  actual:   " + actual.toJson());
    }

    private static String readFixture(String name) {
        String resource = "/fixtures/rift-examples/" + name;
        try (InputStream in = CorpusExpressibilityTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("fixture not found on classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
