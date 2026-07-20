package io.github.achirdlabs.rift;

import io.github.achirdlabs.rift.dsl.ImposterSpec;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Thin async wrappers over {@link Rift}'s synchronous, blocking calls, run on {@link java.util.concurrent.ForkJoinPool#commonPool()}. */
public interface RiftAsync {

    CompletableFuture<Imposter> createAsync(ImposterSpec spec);

    CompletableFuture<Void> deleteAllAsync();

    CompletableFuture<List<Imposter>> impostersAsync();
}
