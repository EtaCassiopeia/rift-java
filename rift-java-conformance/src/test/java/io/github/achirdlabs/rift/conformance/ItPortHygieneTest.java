package io.github.achirdlabs.rift.conformance;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Source guard (#162): no conformance integration test may bind a <em>hardcoded</em> imposter port.
 *
 * <p>A fixed port makes the whole class fail at setup when anything else already holds it — and it
 * fails as a bind error raised from the fixture method, which reads like a broken test rather than
 * an occupied port. It happened for real: an unrelated Docker binding on 4595 turned all six of
 * {@code MatchClauseIT}'s tests into {@code EngineError}s. Two classes had also silently been given
 * the <em>same</em> port (4597), which would collide with nothing but each other the moment they ran
 * concurrently.
 *
 * <p>Omitting the port makes the engine assign a free one and report it back, and every test already
 * addresses its imposter through {@code imp.uri()} / {@code imp.port()} rather than the literal — so
 * the constants bought nothing and cost a whole class of flake.
 *
 * <p>This is a plain unit test, not an IT: it reads source, needs no engine, and so it guards the
 * lanes even when they are gated off. It is a lint-grade textual check, not a proof — a literal
 * reached through enough indirection will slip past it.
 */
class ItPortHygieneTest {

    /**
     * An {@code int} named {@code …port…} assigned a literal — {@code static final int PORT = 4595},
     * but equally a reordered {@code final static}, a non-static field, or a bare local. Deliberately
     * loose about modifiers: the original pattern keyed on {@code static final} and would have waved
     * through the same constant written any other way.
     */
    private static final Pattern PORT_LITERAL_FIELD =
            Pattern.compile("\\bint\\s+\\w*[Pp][Oo][Rr][Tt]\\w*\\s*=\\s*(\\d+)");

    /** {@code .port(4595)} — a literal, as opposed to {@code .port(port)} or {@code .port(imp.port())}. */
    private static final Pattern PORT_LITERAL_CALL = Pattern.compile("\\.port\\(\\s*(\\d+)\\s*\\)");

    private static final Path IT_SOURCES =
            Path.of("src/test/java/io/github/achirdlabs/rift/conformance");

    @TestFactory
    Stream<DynamicTest> noIntegrationTestBindsAHardcodedPort() {
        List<Path> sources = itSources();
        // A silent empty scan would make this guard vacuously green if the layout ever moved, which
        // is the one failure mode a source-reading test must not have. The bound is deliberately
        // well under the current count (8) so adding or removing an IT never trips it — it is here
        // to catch "found nothing at all", not to track the exact number.
        assertTrue(sources.size() >= 5,
                "expected to find the conformance IT sources under " + IT_SOURCES.toAbsolutePath()
                        + " but found " + sources.size() + " — has the layout moved?");

        return sources.stream().map(src -> DynamicTest.dynamicTest(src.getFileName().toString(), () -> {
            String body = read(src);
            String offenders = Stream.concat(
                            matches(PORT_LITERAL_FIELD, body).map(p -> "a fixed port constant (" + p + ")"),
                            matches(PORT_LITERAL_CALL, body).map(p -> "a literal .port(" + p + ")"))
                    .distinct()
                    .collect(Collectors.joining("; "));

            if (!offenders.isEmpty()) {
                fail(src.getFileName() + " binds " + offenders + ". Omit the port so the engine assigns"
                        + " a free one, and address the imposter through imp.uri()/imp.port() — a fixed"
                        + " port fails the whole class whenever anything else already holds it (#162).");
            }
        }));
    }

    private static Stream<String> matches(Pattern pattern, String body) {
        return pattern.matcher(body).results().map(m -> m.group(1));
    }

    /**
     * The conformance package's {@code *IT.java}, non-recursively: those own the imposters they bind.
     *
     * <p>Two neighbours are out of scope on purpose. {@code DslFixtures} keeps its ports because it
     * mirrors the corpus fixtures byte for byte and {@code DslExpressibilityTest} asserts that
     * equality, so dropping them there would break the very fidelity it exists to prove. The
     * testcontainers module keeps its ports because Docker has to publish a fixed port list up
     * front — a different mechanism, not an oversight to be tidied away later.
     */
    private static List<Path> itSources() {
        try (Stream<Path> tree = Files.list(IT_SOURCES)) {
            return tree.filter(p -> p.getFileName().toString().endsWith("IT.java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the conformance IT sources at " + IT_SOURCES, e);
        }
    }

    private static String read(Path src) {
        try {
            return Files.readString(src);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + src, e);
        }
    }
}
