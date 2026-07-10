# rift-java-embedded-jdk21

In-process Rift engine for JDK 21, built from the exact same sources as
[`rift-java-embedded`](../rift-java-embedded/README.md) but compiled with `--release 21
--enable-preview` against the JDK 21 preview Foreign Function & Memory API. The two FFM methods
renamed between JDK 21 preview and JDK 22 stable are resolved reflectively at class load
(`FfmCompat`), so the shared sources build unchanged on either toolchain.

> This artifact is a **temporary bridge**. Once your consumer baseline moves to JDK 22+, switch to
> `rift-java-embedded` and drop this dependency.

> **Depend on exactly one** of `rift-java-embedded` or `rift-java-embedded-jdk21` — never both on the
> same classpath. They publish the same classes (`io.github.etacassiopeia.rift.embedded.*`); with both
> present the JVM loads whichever comes first, and a JDK 21 preview class on a JDK 22+ runtime fails to
> load. The BOM does not manage both together for this reason.

## Required consumer JVM flags

In addition to native access, JDK 21 preview class files require `--enable-preview` at runtime —
the JVM refuses to load preview-flagged classes without it:

```
--enable-preview --enable-native-access=ALL-UNNAMED
```

**Maven Surefire:**

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <argLine>--enable-preview --enable-native-access=ALL-UNNAMED</argLine>
  </configuration>
</plugin>
```

**Gradle:**

```groovy
test {
    jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED")
}
```

The same flags are required at application runtime, not just in tests — pass them to `java`
wherever the process embeds Rift.
