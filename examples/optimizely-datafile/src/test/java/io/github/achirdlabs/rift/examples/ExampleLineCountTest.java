package io.github.achirdlabs.rift.examples;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@link OptimizelyDatafileExample}'s {@code main()} body against creeping past a
 * skimmable size: this is a showcase sample, not production code, so if it needs more than ~30
 * lines to say what it demonstrates, either the example or the API it exercises needs rethinking.
 *
 * <p>Pure source-text read — no engine, no native library — so this runs on every CI lane without
 * a {@code librift_ffi} cdylib. Locates the source file via the {@code basedir} system property
 * Surefire always injects (falling back to {@code user.dir} for IDE-driven runs), which resolves
 * correctly whether this module builds standalone or as part of the reactor.
 */
class ExampleLineCountTest {

    private static final int MAX_MAIN_BODY_LINES = 30;
    private static final String RELATIVE_SOURCE_PATH =
            "src/main/java/io/github/achirdlabs/rift/examples/OptimizelyDatafileExample.java";

    @Test
    void mainBodyStaysUnderTheLineBudget() throws IOException {
        Path source = sourceFile();
        List<String> lines = Files.readAllLines(source);
        List<String> body = mainMethodBody(lines);
        long codeLines = body.stream().map(String::strip).filter(ExampleLineCountTest::isCodeLine).count();

        assertTrue(codeLines <= MAX_MAIN_BODY_LINES,
                "main() body of " + source.getFileName() + " has " + codeLines
                        + " code lines, over the " + MAX_MAIN_BODY_LINES + "-line showcase budget");
    }

    private static Path sourceFile() {
        String baseDir = System.getProperty("basedir", System.getProperty("user.dir"));
        Path candidate = Path.of(baseDir, RELATIVE_SOURCE_PATH);
        assertTrue(Files.exists(candidate), "example source not found at " + candidate);
        return candidate;
    }

    /** Extracts the {@code main(String[] args)} method's body, between its opening and matching closing brace. */
    private static List<String> mainMethodBody(List<String> lines) {
        int signatureLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("static void main(")) {
                signatureLine = i;
                break;
            }
        }
        assertTrue(signatureLine >= 0, "no main(String[] args) method found");

        int depth = 0;
        int bodyStart = -1;
        for (int i = signatureLine; i < lines.size(); i++) {
            for (char c : lines.get(i).toCharArray()) {
                if (c == '{') {
                    if (depth == 0) {
                        bodyStart = i + 1;
                    }
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        assertTrue(bodyStart >= 0, "could not find the start of main()'s body");
                        return lines.subList(bodyStart, i);
                    }
                }
            }
        }
        throw new AssertionError("could not find the end of main()'s body");
    }

    /** A "code" line: not blank, not a lone brace, not an import/package line, not a comment. */
    private static boolean isCodeLine(String line) {
        if (line.isEmpty() || line.equals("{") || line.equals("}")) {
            return false;
        }
        if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
            return false;
        }
        return !line.startsWith("import ") && !line.startsWith("package ");
    }
}
