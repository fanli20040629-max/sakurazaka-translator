package com.fanli.sakurazakatranslator.translation;

import com.fanli.sakurazakatranslator.domain.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One physical request at a time. Invalidating a UI never pretends the request already ended. */
public final class TranslationRunner implements AutoCloseable {
    private final Executor callbackExecutor;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService canceller = Executors.newSingleThreadScheduledExecutor();
    private TranslationProvider active;
    private long revision;
    private boolean closed;
    private boolean cancelRequested;

    public TranslationRunner(Executor callbackExecutor) { this.callbackExecutor = callbackExecutor; }

    public synchronized boolean start(TranslationRequest request, TranslationProvider provider,
                                      Consumer<TranslationResult> success, Consumer<String> failure) {
        if (closed || active != null) return false;
        long ticket = ++revision;
        active = provider;
        cancelRequested = false;
        AtomicBoolean expired = new AtomicBoolean();
        // Schedule before submitting the worker so close() cannot shut down this scheduler first.
        ScheduledFuture<?> deadline = canceller.schedule(() -> {
            expired.set(true);
            provider.cancel();
        }, 45, TimeUnit.SECONDS);
        worker.execute(() -> {
            TranslationResult result = null;
            String error = null;
            try {
                result = TranslationValidator.validate(request, provider.translate(request));
                if (!result.accepted) error = "VALIDATION";
            } catch (IOException e) {
                // Provider supplies safe codes only; arbitrary exception messages are not shown.
                error = e instanceof DeepSeekFailure ? e.getMessage() : "NETWORK";
            } catch (RuntimeException e) {
                error = "VALIDATION";
            } finally {
                deadline.cancel(false);
                synchronized (this) { active = null; }
            }
            TranslationResult completed = result;
            String reason = expired.get() ? "TIMEOUT" : error;
            callbackExecutor.execute(() -> {
                synchronized (this) {
                    if (closed || ticket != revision) return;
                    if (reason == null) success.accept(completed);
                    else failure.accept(reason);
                }
            });
        });
        return true;
    }

    public synchronized void cancel() {
        revision++;
        if (active != null && !closed && !cancelRequested) {
            cancelRequested = true;
            TranslationProvider canceled = active;
            canceller.execute(canceled::cancel);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        cancel();
        closed = true;
        worker.shutdown();
        // Keep an in-flight deadline and queued disconnect alive until physical work finishes.
        canceller.shutdown();
    }

    /** Safe error category, never a remote response or exception containing credentials. */
    public static final class DeepSeekFailure extends IOException {
        private static final long serialVersionUID = 1L;
        public DeepSeekFailure(String code) { super(code); }
    }
}
