package net.omni.extraction.data;

import net.omni.extraction.ExtractionPlugin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The single off-thread database worker for the plugin. All SQLite/MySQL work
 * runs on a dedicated, named, daemon thread pool — never on the Bukkit main
 * thread. Exposes a {@link CompletableFuture}-based API plus a "keyed" variant
 * that serializes every operation for the same key (a player UUID, a party id,
 * ...), so a load/save/mutation for one entity can never interleave or
 * overwrite stale data.
 *
 * <p>Continuations that touch Bukkit (caches, messages, plugin messages) hop
 * back to the main thread through {@link #mainExecutor()}. On
 * {@link #shutdown(long)} the worker drains in-flight/queued work, then stops;
 * the owners are responsible for a final synchronous flush before closing the
 * datasource.</p>
 *
 * <p>SQLite has a single writer and locks aggressively, so it runs on a
 * one-thread pool (global FIFO order, which also implies per-key order). MySQL
 * uses a fixed pool sized by {@code storage.threads} with per-key ordering.</p>
 */
public final class AsyncDatabase {

    private final ExtractionPlugin plugin;
    private final ExecutorService pool;
    private final Map<Object, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown;

    public AsyncDatabase(ExtractionPlugin plugin, int threads, boolean serializedWrites) {
        this.plugin = plugin;

        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "Extraction-DB-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };

        this.pool = serializedWrites
                ? Executors.newSingleThreadExecutor(factory)
                : Executors.newFixedThreadPool(Math.max(1, threads), factory);
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    /** Runs {@code work} on the DB pool and completes the future with its result. */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            pool.execute(() -> {
                try {
                    future.complete(work.get());
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.SEVERE, "Async database task failed", throwable);
                    future.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException rejected) {
            future.completeExceptionally(rejected);
        }
        return future;
    }

    /** Runs {@code work} on the DB pool. */
    public CompletableFuture<Void> runAsync(Runnable work) {
        return supplyAsync(() -> {
            work.run();
            return null;
        });
    }

    /**
     * Serializes {@code work} against every other operation submitted with the
     * same {@code key} (the tail of a per-key chain), then runs it on the DB
     * pool. Guarantees ordering for a single entity without blocking a pool
     * thread while it waits.
     */
    public <T> CompletableFuture<T> supplyKeyed(Object key, Supplier<T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();

        tails.compute(key, (k, previous) -> {
            CompletableFuture<Void> base = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous;

            CompletableFuture<Void> next = base.handle((value, error) -> null).thenRunAsync(
                    () -> {
                        try {
                            result.complete(work.get());
                        } catch (Throwable throwable) {
                            plugin.getLogger().log(Level.SEVERE, "Async database task failed", throwable);
                            result.completeExceptionally(throwable);
                        }
                    }, pool);

            next.whenComplete((value, error) -> {
                tails.remove(k, next);

                // If the pool rejected the task the lambda never ran, so make sure
                // the result future is settled instead of hanging forever.
                if (error != null)
                    result.completeExceptionally(error);
            });
            return next;
        });

        return result;
    }

    public CompletableFuture<Void> runKeyed(Object key, Runnable work) {
        return supplyKeyed(key, () -> {
            work.run();
            return null;
        });
    }

    /**
     * Executor that runs a continuation on the Bukkit main thread. Once the
     * plugin is disabled the scheduler no longer accepts tasks; those
     * continuations are dropped (shutdown performs a synchronous final flush).
     */
    public Executor mainExecutor() {
        return task -> {
            if (shuttingDown || !plugin.isEnabled())
                return;

            try {
                plugin.getServer().getScheduler().runTask(plugin, task);
            } catch (IllegalStateException ignored) {
                // Scheduler torn down between the enabled-check and the call.
            }
        };
    }

    /**
     * Stops accepting new work, waits up to {@code timeoutMillis} for the
     * per-key chains and the pool to drain, then shuts it down.
     */
    public void shutdown(long timeoutMillis) {
        shuttingDown = true;

        long deadline = System.currentTimeMillis() + timeoutMillis;

        for (CompletableFuture<Void> tail : tails.values()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0)
                break;

            try {
                tail.get(remaining, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
        }

        tails.clear();

        pool.shutdown();

        try {
            if (!pool.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS))
                pool.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }
}
