package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.IsSpec;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;

/**
 * A live intercept (TLS-MITM forward-proxy) listener: point an HTTPS client's proxy at
 * {@link #address()}/{@link #proxySelector()} (with {@link #trust()} in its trust store), then
 * add rules deciding what happens to each intercepted host — answer inline ({@link #serve}),
 * forward to a plain {@code host:port} ({@link #forward}), or forward to one of this SDK's own
 * {@link Imposter}s ({@link #redirectTo}).
 *
 * <p>Obtained via {@link Rift#intercept()}/{@link Rift#intercept(InterceptOptions)}; at most one
 * per engine — a second call throws {@link IllegalStateException}.
 */
public interface Intercept extends AutoCloseable {

    /** The intercept listener's bound address, for {@code http.proxyHost}/{@code http.proxyPort}-style configuration. */
    InetSocketAddress address();

    /** The intercept listener's base URL. */
    URI uri();

    /** A {@link ProxySelector} routing every request through this intercept — convenience for {@code java.net.http.HttpClient}. */
    ProxySelector proxySelector();

    /** Adds a rule answering requests to {@code host} directly with {@code response}, without contacting the real host. */
    InterceptRule serve(String host, IsSpec response);

    /** Adds a rule forwarding requests to {@code host} on to {@code hostPort} (a {@code host:port} on localhost). */
    InterceptRule forward(String host, String hostPort);

    /** Adds a rule forwarding requests to {@code host} on to {@code imposter}'s own port. */
    InterceptRule redirectTo(String host, Imposter imposter);

    /** The current intercept rules, in the order they were added. */
    List<InterceptRule> rules();

    /** Removes every intercept rule. */
    void clearRules();

    /** Trust material for this intercept's CA. */
    InterceptTrust trust();

    /** Clears this intercept's rules; the listener itself is torn down when the owning {@link Rift} is closed. */
    @Override
    void close();
}
