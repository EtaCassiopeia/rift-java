package io.github.achirdlabs.rift.verify;

/**
 * Optional sections to ask the engine for on a {@code verifyResult} call. Each one costs work and
 * wire bytes, so none is requested by default — a bare {@code verifyResult} returns just the counts.
 */
public enum VerifyDetail {

    /** Return the matching requests themselves, not just {@code matched}. */
    REQUESTS,

    /** Return the best-scoring non-match and the predicate clauses it failed, for diff rendering. */
    CLOSEST
}
