package java.util.concurrent;

import java.util.ArrayList;
import java.util.List;

/** Deterministic JVM oracle for the Java 21 preview policy API consumed by Juno's QEMU fixture. */
public class StructuredTaskScope<T> implements AutoCloseable {
    public interface Subtask<T> {
        T get();
    }

    static final class Task<T> implements Subtask<T> {
        private final Callable<? extends T> callable;
        private T result;
        private Throwable failure;
        private boolean complete;

        Task(Callable<? extends T> callable) {
            this.callable = callable;
        }

        void run() {
            try {
                result = callable.call();
            } catch (Throwable throwable) {
                failure = throwable;
            }
            complete = true;
        }

        @Override
        public T get() {
            if (!complete || failure != null) throw new IllegalStateException("Subtask did not complete successfully");
            return result;
        }
    }

    final List<Task<? extends T>> tasks = new ArrayList<>();

    public <U extends T> Subtask<U> fork(Callable<? extends U> callable) {
        Task<U> task = new Task<>(callable);
        tasks.add(task);
        return task;
    }

    public StructuredTaskScope<T> join() throws InterruptedException {
        tasks.forEach(Task::run);
        return this;
    }

    @Override
    public void close() {
    }

    public static final class ShutdownOnFailure extends StructuredTaskScope<Object> {
        private Throwable failure;

        @Override
        public ShutdownOnFailure join() {
            for (Task<?> task : tasks) {
                task.run();
                if (task.failure != null) {
                    failure = task.failure;
                    break;
                }
            }
            return this;
        }

        public void throwIfFailed() throws ExecutionException {
            if (failure != null) throw new ExecutionException(failure.getMessage(), failure);
        }
    }

    public static final class ShutdownOnSuccess<T> extends StructuredTaskScope<T> {
        private T result;
        private Throwable failure;
        private boolean succeeded;

        @Override
        public ShutdownOnSuccess<T> join() {
            for (Task<? extends T> task : tasks) {
                task.run();
                if (task.failure == null) {
                    result = task.result;
                    succeeded = true;
                    break;
                }
                if (failure == null) failure = task.failure;
            }
            return this;
        }

        public T result() throws ExecutionException {
            if (!succeeded) throw new ExecutionException(failure);
            return result;
        }
    }
}
