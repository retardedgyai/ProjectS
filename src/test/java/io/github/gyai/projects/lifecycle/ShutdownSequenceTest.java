package io.github.gyai.projects.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ShutdownSequenceTest {
    private ShutdownSequenceTest() {
    }

    public static void main(String[] args) {
        continuesAfterFirstAndMiddleFailure();
        runsSuccessfulSequenceOnce();
        skipsUninitializedComponents();
        loggingFailureDoesNotStopCleanup();
        doesNotSwallowError();
    }

    private static void continuesAfterFirstAndMiddleFailure() {
        List<String> executed = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        ShutdownSequence sequence = new ShutdownSequence(
                (name, exception) -> failures.add(name));
        sequence.add("first", () -> {
            executed.add("first");
            throw new IllegalStateException("first failed");
        });
        sequence.add("middle", () -> executed.add("middle"));
        sequence.add("third", () -> {
            executed.add("third");
            throw new IllegalArgumentException("third failed");
        });
        sequence.add("last", () -> executed.add("last"));
        sequence.run();

        assert executed.equals(List.of("first", "middle", "third", "last"));
        assert failures.equals(List.of("first", "third"));
    }

    private static void runsSuccessfulSequenceOnce() {
        AtomicInteger calls = new AtomicInteger();
        ShutdownSequence sequence = new ShutdownSequence(
                (name, exception) -> {
                    throw new AssertionError("Unexpected failure: " + name);
                });
        sequence.add("stop", calls::incrementAndGet);
        sequence.add("clear", calls::incrementAndGet);
        sequence.run();
        sequence.run();

        assert calls.get() == 2;
        assert sequence.executed();
    }

    private static void skipsUninitializedComponents() {
        AtomicInteger calls = new AtomicInteger();
        ShutdownSequence sequence = new ShutdownSequence(
                (name, exception) -> { });
        sequence.addIfPresent("missing", null,
                ignored -> calls.incrementAndGet());
        sequence.addIfPresent("initialized", "manager",
                ignored -> calls.incrementAndGet());
        sequence.run();
        assert calls.get() == 1;
    }

    private static void loggingFailureDoesNotStopCleanup() {
        AtomicInteger calls = new AtomicInteger();
        ShutdownSequence sequence = new ShutdownSequence(
                (name, exception) -> {
                    throw new IllegalStateException("logger unavailable");
                });
        sequence.add("failure", () -> {
            throw new IllegalStateException("cleanup failed");
        });
        sequence.add("last", calls::incrementAndGet);
        sequence.run();
        assert calls.get() == 1;
    }

    private static void doesNotSwallowError() {
        ShutdownSequence sequence = new ShutdownSequence(
                (name, exception) -> { });
        sequence.add("fatal", () -> {
            throw new AssertionError("fatal");
        });
        try {
            sequence.run();
            throw new AssertionError("Expected Error to escape");
        } catch (AssertionError expected) {
            assert expected.getMessage().equals("fatal");
        }
    }
}
