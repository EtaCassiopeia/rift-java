package io.github.achirdlabs.rift;

/**
 * What an {@link InterceptRule} does with a matched intercepted request. Mirrors the engine's
 * {@code InterceptAction} enum ({@code Serve}/{@code Forward}); {@link #REDIRECT} is an SDK-level
 * distinction with no separate wire representation — {@link Intercept#redirectTo} rides the same
 * {@code forward} action as {@link Intercept#forward}, targeting the given {@link Imposter}'s own
 * port. Because of that, a rule read back from {@link Intercept#rules()} can only ever report
 * {@link #SERVE} or {@link #FORWARD} — the engine has no way to echo back that a given
 * {@code forward} action was originally created via {@code redirectTo}.
 */
public enum RuleKind {
    SERVE,
    FORWARD,
    REDIRECT
}
