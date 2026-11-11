package com.packstudio.core;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Managed background I/O executor for PackStudio metadata inspection and icon decoding.
 *
 * <p>Uses a bounded pool of daemon worker threads so background pack archive reads never block
 * the Minecraft client render thread, never starve vanilla chunk/asset pools, and never prevent
 * clean JVM shutdown.</p>
 */
public final class PackStudioExecutors {
    private static final int WORKER_COUNT =
            Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 4);

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger(1);

    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(
            WORKER_COUNT,
            runnable -> {
                Thread thread = new Thread(runnable, "PackStudio-IO-" + THREAD_SEQ.getAndIncrement());
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler((t, err) ->
                        PackStudioConstants.LOGGER.error("Uncaught exception on {}", t.getName(), err));
                return thread;
            }
    );

    private PackStudioExecutors() {
    }

    /**
     * Returns the shared bounded background executor for non-blocking disk and archive I/O.
     */
    public static Executor ioExecutor() {
        return IO_EXECUTOR;
    }
}
