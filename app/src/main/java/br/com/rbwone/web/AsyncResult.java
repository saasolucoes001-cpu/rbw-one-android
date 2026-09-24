package br.com.rbwone.web;

import com.google.android.gms.tasks.TaskCompletionSource;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Uses the existing Google Tasks dependency so completion also works on Android 6. */
public final class AsyncResult<T> {
    private final TaskCompletionSource<T> source = new TaskCompletionSource<>();
    public boolean complete(T value) { return source.trySetResult(value); }
    public boolean completeExceptionally(Throwable error) { return source.trySetException(error instanceof Exception ? (Exception) error : new IllegalStateException(error)); }
    public void whenComplete(BiConsumer<T, Throwable> callback) {
        source.getTask().addOnCompleteListener(Runnable::run, task -> callback.accept(task.isSuccessful() ? task.getResult() : null, task.getException()));
    }
    public void thenAccept(Consumer<T> callback) { whenComplete((value, error) -> { if (error == null) callback.accept(value); }); }
    public static <T> AsyncResult<T> completedFuture(T value) { AsyncResult<T> result = new AsyncResult<>(); result.complete(value); return result; }
    public static <T> AsyncResult<T> failedFuture(Throwable error) { AsyncResult<T> result = new AsyncResult<>(); result.completeExceptionally(error); return result; }
    public static <T> AsyncResult<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        AsyncResult<T> result = new AsyncResult<>();
        executor.execute(() -> { try { result.complete(supplier.get()); } catch (Exception error) { result.completeExceptionally(error); } });
        return result;
    }
}
