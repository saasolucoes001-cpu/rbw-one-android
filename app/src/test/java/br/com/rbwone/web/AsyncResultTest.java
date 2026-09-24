package br.com.rbwone.web;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class AsyncResultTest {
    @Test public void completionBeforeListenerStillReachesListener() {
        AtomicReference<String> value = new AtomicReference<>();
        AsyncResult.completedFuture("ready").thenAccept(value::set);
        assertEquals("ready", value.get());
    }
    @Test public void onlyFirstCompletionWinsForPermissionWaiter() {
        AsyncResult<String> result = new AsyncResult<>(); AtomicInteger completions = new AtomicInteger();
        result.whenComplete((value, error) -> { assertEquals("granted", value); assertNull(error); completions.incrementAndGet(); });
        assertTrue(result.complete("granted")); assertFalse(result.complete("denied")); assertFalse(result.completeExceptionally(new IllegalStateException())); assertEquals(1, completions.get());
    }
    @Test public void asyncFailureReachesListenerWithoutSuccessCallback() {
        AtomicReference<Throwable> failure = new AtomicReference<>(); AtomicInteger successes = new AtomicInteger();
        AsyncResult<String> result = AsyncResult.supplyAsync(() -> { throw new IllegalStateException("offline"); }, Runnable::run);
        result.whenComplete((value, error) -> { assertNull(value); failure.set(error); }); result.thenAccept(value -> successes.incrementAndGet());
        assertEquals("offline", failure.get().getMessage()); assertEquals(0, successes.get());
    }
}
