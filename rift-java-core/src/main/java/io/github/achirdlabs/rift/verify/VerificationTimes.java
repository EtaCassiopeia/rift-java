package io.github.achirdlabs.rift.verify;

/**
 * How many times a matched request is expected to have been recorded, for {@code Imposter.verify}
 * / {@code Space.verify}. Immutable; construct via the static factories.
 */
public final class VerificationTimes {

    private enum Kind { EXACTLY, AT_LEAST, AT_MOST, BETWEEN }

    private final Kind kind;
    private final int min;
    private final int max;

    private VerificationTimes(Kind kind, int min, int max) {
        this.kind = kind;
        this.min = min;
        this.max = max;
    }

    /** Expect exactly {@code n} matching requests. */
    public static VerificationTimes times(int n) {
        return new VerificationTimes(Kind.EXACTLY, n, n);
    }

    /** Alias for {@link #times(int)}. */
    public static VerificationTimes exactly(int n) {
        return times(n);
    }

    /** Expect at least {@code n} matching requests. */
    public static VerificationTimes atLeast(int n) {
        return new VerificationTimes(Kind.AT_LEAST, n, Integer.MAX_VALUE);
    }

    /** Expect at most {@code n} matching requests. */
    public static VerificationTimes atMost(int n) {
        return new VerificationTimes(Kind.AT_MOST, 0, n);
    }

    /** Expect between {@code min} and {@code max} matching requests, inclusive. */
    public static VerificationTimes between(int min, int max) {
        return new VerificationTimes(Kind.BETWEEN, min, max);
    }

    /** Expect zero matching requests — equivalent to {@code exactly(0)}. */
    public static VerificationTimes never() {
        return times(0);
    }

    /** Whether {@code count} satisfies this expectation. */
    public boolean matches(int count) {
        return count >= min && count <= max;
    }

    /** A human-readable description of this expectation, e.g. {@code "at least 2 times"}. */
    public String describe() {
        if (kind == Kind.EXACTLY && min == 0) {
            return "never";
        }
        if (kind == Kind.EXACTLY) {
            return "exactly " + timesText(min);
        }
        if (kind == Kind.AT_LEAST) {
            return "at least " + timesText(min);
        }
        if (kind == Kind.AT_MOST) {
            return "at most " + timesText(max);
        }
        return "between " + min + " and " + timesText(max);
    }

    private static String timesText(int n) {
        return n + (n == 1 ? " time" : " times");
    }
}
