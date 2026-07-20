# rift-java-junit5

JUnit 5 test integration: `@RiftTest` starts one `Rift` engine per test class, creates
`@RiftImposter`-declared imposters against it, and injects them (and the `Rift` client itself)
via `@InjectImposter`/`@InjectRift`.

```java
import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.dsl.ImposterSpec;
import io.github.achirdlabs.rift.junit5.*;
import static io.github.achirdlabs.rift.dsl.RiftDsl.*;

@RiftTest
class UserClientTest {

    @RiftImposter
    static ImposterSpec usersSpec = imposter("users").record()
            .stub(onGet("/api/users/1").willReturn(okJson("{\"id\":1}")));

    @InjectImposter("users") Imposter users;

    @Test
    void fetchesUser() {
        users.verify(onGet("/api/users/1"), times(1));
    }
}
```

```xml
<dependency>
  <groupId>io.github.achird-labs</groupId>
  <artifactId>rift-java-junit5</artifactId>
  <scope>test</scope>
</dependency>
```

`@RiftTest(transport = ...)` picks how the engine is obtained (`AUTO`/`CONNECT`/`SPAWN`/
`EMBEDDED`), and `reset = ...` controls whether/when configured imposters are cleared between
test methods (`PER_TEST`/`PER_CLASS`/`NONE`). See [../docs/junit5.md](../docs/junit5.md) for the
full attribute reference, reset semantics, and parallel-execution notes.
