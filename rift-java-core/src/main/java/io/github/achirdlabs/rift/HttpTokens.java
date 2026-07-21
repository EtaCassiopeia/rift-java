package io.github.achirdlabs.rift;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validates the HTTP token grammar (RFC 9110 §5.6.2) for the {@code match=} clause values that must
 * be one — a header name and a request method. Shared so both hold to the same definition of a
 * token rather than drifting apart.
 */
final class HttpTokens {

    private static final Pattern TOKEN = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

    private HttpTokens() {}

    /**
     * Accepts {@code value} only if it is an HTTP token, naming it {@code what} in the failure.
     *
     * @throws IllegalArgumentException if {@code value} is not an HTTP token
     */
    static void require(String value, String what) {
        Objects.requireNonNull(value, what);
        if (!TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("not a valid " + what + " (RFC 9110 token): \"" + value + "\"");
        }
    }
}
