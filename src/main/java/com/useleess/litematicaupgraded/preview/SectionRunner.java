package com.useleess.litematicaupgraded.preview;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Runs one piece of work per item on a pool and returns when all of them are over; once stopped, items not yet started are skipped. */
final class SectionRunner {
    private SectionRunner() {}

    /** @param stop asked before each item starts; the first error of any item also stops the rest and is rethrown here */
    static <T> void runAll(Executor pool, List<T> items, BooleanSupplier stop, Consumer<T> work) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(items.size());
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        for (T item : items) {
            pool.execute(() -> {
                try {
                    if (!stop.getAsBoolean() && failure.get() == null) work.accept(item);
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        if (failure.get() != null) throw failure.get();
    }
}
