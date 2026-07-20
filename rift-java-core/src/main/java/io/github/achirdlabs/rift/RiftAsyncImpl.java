package io.github.achirdlabs.rift;

import io.github.achirdlabs.rift.dsl.ImposterSpec;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class RiftAsyncImpl implements RiftAsync {

    private final Rift rift;

    RiftAsyncImpl(Rift rift) {
        this.rift = rift;
    }

    @Override
    public CompletableFuture<Imposter> createAsync(ImposterSpec spec) {
        return CompletableFuture.supplyAsync(() -> rift.create(spec));
    }

    @Override
    public CompletableFuture<Void> deleteAllAsync() {
        return CompletableFuture.runAsync(rift::deleteAll);
    }

    @Override
    public CompletableFuture<List<Imposter>> impostersAsync() {
        return CompletableFuture.supplyAsync(rift::imposters);
    }
}
