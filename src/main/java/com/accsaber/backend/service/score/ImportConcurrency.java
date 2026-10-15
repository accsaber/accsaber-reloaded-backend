package com.accsaber.backend.service.score;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;

final class ImportConcurrency {

    private ImportConcurrency() {
    }

    static <T, R> List<R> fanOut(Collection<T> items, Executor executor, Function<T, R> task) {
        return items.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> task.apply(item), executor))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();
    }

    static <T> void forEachBounded(Collection<T> items, int maxConcurrent, Consumer<T> task) {
        Semaphore semaphore = new Semaphore(maxConcurrent);
        List<Thread> threads = items.stream()
                .map(item -> Thread.startVirtualThread(() -> {
                    semaphore.acquireUninterruptibly();
                    try {
                        task.accept(item);
                    } finally {
                        semaphore.release();
                    }
                }))
                .toList();
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
