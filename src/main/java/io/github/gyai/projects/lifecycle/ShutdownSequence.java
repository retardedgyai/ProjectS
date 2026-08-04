package io.github.gyai.projects.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Executes named cleanup actions once while isolating RuntimeException failures. */
public final class ShutdownSequence {
    private final List<Step> steps = new ArrayList<>();
    private final BiConsumer<String, RuntimeException> failureLogger;
    private final AtomicBoolean executed = new AtomicBoolean();

    public ShutdownSequence(BiConsumer<String, RuntimeException> failureLogger) {
        this.failureLogger = Objects.requireNonNull(failureLogger, "failureLogger");
    }

    public ShutdownSequence add(String name, Runnable action) {
        if (executed.get()) {
            throw new IllegalStateException(
                    "Cannot add cleanup steps after shutdown has started");
        }
        steps.add(new Step(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(action, "action")));
        return this;
    }

    public <T> ShutdownSequence addIfPresent(
            String name,
            T component,
            Consumer<? super T> cleanup
    ) {
        Objects.requireNonNull(cleanup, "cleanup");
        if (component != null) {
            add(name, () -> cleanup.accept(component));
        }
        return this;
    }

    public void run() {
        if (!executed.compareAndSet(false, true)) {
            return;
        }
        for (Step step : List.copyOf(steps)) {
            try {
                step.action().run();
            } catch (RuntimeException exception) {
                try {
                    failureLogger.accept(step.name(), exception);
                } catch (RuntimeException ignored) {
                    // Logging must not prevent later cleanup actions.
                }
            }
        }
    }

    public boolean executed() {
        return executed.get();
    }

    private record Step(String name, Runnable action) {
    }
}
