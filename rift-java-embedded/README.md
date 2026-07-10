# rift-java-embedded

In-process Rift engine over the stable Panama Foreign Function & Memory API (JDK 22+),
binding the `librift_ffi` C-ABI v2.

## Required consumer JVM flags

FFM downcalls are a restricted operation. Consumers must grant native access on the module
(or class path entry) that loads `rift-java-embedded`, or the JVM prints an enforced warning
(and a future JDK release may turn this into a hard error):

```
--enable-native-access=ALL-UNNAMED
```

**Maven Surefire:**

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <argLine>--enable-native-access=ALL-UNNAMED</argLine>
  </configuration>
</plugin>
```

**Gradle:**

```groovy
test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
```

The same flag is required at application runtime, not just in tests — pass it to `java` wherever
the process embeds Rift.

## JDK 21 consumers

If your project's baseline is JDK 21, use
[`rift-java-embedded-jdk21`](../rift-java-embedded-jdk21/README.md) instead — it is built from
these same sources against the JDK 21 preview FFM API and additionally requires
`--enable-preview`. It is a temporary bridge, dropped once the consumer baseline moves to 22.

Depend on **exactly one** of the two — never both on the same classpath: they ship the same classes,
so with both present the JVM loads whichever comes first (a preview-flagged JDK 21 class fails to load
on a JDK 22+ runtime).
