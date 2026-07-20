# rift-java-spring

Spring Boot test integration: `@EnableRift` starts one `Rift` engine for the (cacheable) Spring
test application context, `@ConfigureImposter` declares imposters to create against it and
publishes their URI/port as environment properties, and `@InjectImposter`/`@InjectRift` inject
them into test fields. Spring is a `provided` dependency — bring your own Spring Boot version.

```java
import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.spring.*;
import org.springframework.boot.test.context.SpringBootTest;
import static io.github.achirdlabs.rift.dsl.RiftDsl.*;

@SpringBootTest
@EnableRift
@ConfigureImposter(name = "users", baseUrlProperty = "users.base-url")
class UserClientTest {

    @InjectImposter("users") Imposter users;

    @Test
    void fetchesUser() {
        users.addStub(onGet("/api/users/1").willReturn(okJson("{\"id\":1}")));
        // the application context reads "users.base-url" — no test-specific wiring needed
        users.verify(onGet("/api/users/1"), times(1));
    }
}
```

```xml
<dependency>
  <groupId>io.github.achird-labs</groupId>
  <artifactId>rift-java-spring</artifactId>
  <scope>test</scope>
</dependency>
```

`@ConfigureImposter` is repeatable; its default `spec()` creates a bare recording imposter
(`imposter(name).record()`) — pass a `Supplier<ImposterSpec>` class for custom stubbing.
`@EnableRift`'s attributes (plus every `@ConfigureImposter`) contribute to the Spring test
context cache key, so identically-configured test classes share one context and one engine.
`@EnableRift(transport = ...)` picks how the engine is obtained: `CONNECT` requires `adminUri`,
`SPAWN` always launches a managed `rift` binary, and `AUTO` connects if `adminUri` is set or
spawns otherwise (unlike the JUnit 5 module's `AUTO`, this does not yet prefer an embedded engine
— tracked separately). `reset = ...` (`PER_TEST`/`PER_CLASS`/`NONE`) behaves the same as the
JUnit 5 module's `Reset` — see [../docs/junit5.md](../docs/junit5.md) for the semantics table
(this module has its own, separate `Reset`/`Transport` enums, by design — `rift-java-spring`
does not depend on `rift-java-junit5`).
