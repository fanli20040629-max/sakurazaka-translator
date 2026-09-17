package com.fanli.sakurazakatranslator.translation;

import com.fanli.sakurazakatranslator.domain.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class TranslationRunnerSelfTest {
    public static void main(String[] args) throws Exception {
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger shown = new AtomicInteger(), canceled = new AtomicInteger();
        TranslationProvider provider = new TranslationProvider() {
            public TranslationResult translate(TranslationRequest request) throws IOException {
                entered.countDown();
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("test timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                return new TranslationResult(List.of(new TranslationResult.Item("m", "你好")), List.of(), true);
            }
            public void cancel() { canceled.incrementAndGet(); }
        };
        var message = new ChatMessage("m", "こんにちは", "NODE_TEXT", 0, 0, 20, 20,
                "SCREEN", List.of("n"), List.of());
        var request = new TranslationRequest(new PageToken(1, "app", 1, 1),
                List.of(message), null, true);
        try (TranslationRunner runner = new TranslationRunner(callbacks::add)) {
            if (!runner.start(request, provider, r -> shown.incrementAndGet(), e -> shown.incrementAndGet())) {
                throw new AssertionError("first start rejected");
            }
            if (!entered.await(2, TimeUnit.SECONDS)) throw new AssertionError("worker never started");
            if (runner.start(request, provider, r -> {}, e -> {})) throw new AssertionError("parallel request");
            runner.cancel();
            if (runner.start(request, provider, r -> {}, e -> {})) {
                throw new AssertionError("cancel must not pretend physical request finished");
            }
            release.countDown();
            Runnable late = callbacks.poll(3, TimeUnit.SECONDS);
            if (late == null) throw new AssertionError("missing completion");
            late.run();
            if (shown.get() != 0) throw new AssertionError("late result reached old page");
            if (!runner.start(request, provider, r -> shown.incrementAndGet(), e -> {})) {
                throw new AssertionError("completion did not release occupancy");
            }
            Runnable current = callbacks.poll(3, TimeUnit.SECONDS);
            if (current == null) throw new AssertionError("missing new completion");
            current.run();
            if (shown.get() != 1) throw new AssertionError("new result missing");
        }
        System.out.println("TranslationRunnerSelfTest PASS (single flight, cancellation, late response, reuse)");
    }
}
