package com.sajal.VtPipe.core.impl;

import com.sajal.VtPipe.core.exception.PipeCancellationException;
import com.sajal.VtPipe.core.exception.PipeException;
import com.sajal.VtPipe.core.executor.VirtualThreadExecutor;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Basic implementation of a Pipe that executes a single task using virtual threads.
 *
 * @param <T> the type of result produced by this pipe
 */
public class SinglePipe<T> extends BasePipe<T> {

	private final Future<T> future;
	private final Supplier<T> taskSupplier;
	private volatile boolean hasStarted = false;
	private volatile boolean hasFinished = false;
	private volatile boolean wasHalted = false;
	private volatile Throwable exception = null;
	private volatile T result = null;

	/**
	 * Creates a new SinglePipe from a Supplier using the default executor.
	 *
	 * @param supplier the supplier that will provide the result
	 */
	public SinglePipe(Supplier<T> supplier) {
		this(VirtualThreadExecutor.getExecutor(), supplier);
	}

	/**
	 * Creates a new SinglePipe from a Supplier using the specified executor.
	 *
	 * @param executor the executor service to use
	 * @param supplier the supplier that will provide the result
	 */
	public SinglePipe(ExecutorService executor, Supplier<T> supplier) {
		Objects.requireNonNull(supplier, "Supplier cannot be null");
		this.executor = Objects.requireNonNull(executor, "Executor cannot be null");
		this.taskSupplier = supplier;

		// Use explicit Callable for type safety
		this.future = executor.submit(new Callable<T>() {
			@Override
			public T call() throws Exception {
				hasStarted = true;
				try {
					T value = supplier.get();
					result = value;
					hasFinished = true;
					return value;
				} catch (Throwable e) {
					exception = e;
					hasFinished = true;
					if (e instanceof RuntimeException) {
						throw (RuntimeException) e;
					} else if (e instanceof Error) {
						throw (Error) e;
					} else {
						throw new RuntimeException(e.getMessage(), e);
					}
				}
			}
		});
	}

	/**
	 * Creates a new SinglePipe from a Callable.
	 *
	 * @param callable the callable that will provide the result
	 */
	public SinglePipe(Callable<T> callable) {
		this(VirtualThreadExecutor.getExecutor(), callable);
	}

	/**
	 * Creates a new SinglePipe from a Callable using the specified executor.
	 *
	 * @param executor the executor service to use
	 * @param callable the callable that will provide the result
	 */
	public SinglePipe(ExecutorService executor, Callable<T> callable) {
		Objects.requireNonNull(callable, "Callable cannot be null");
		this.executor = Objects.requireNonNull(executor, "Executor cannot be null");
		this.taskSupplier = () -> {
			try {
				return callable.call();
			} catch (Exception e) {
				if (e instanceof RuntimeException) {
					throw (RuntimeException) e;
				} else {
					throw new PipeException("Callable execution failed", e);
				}
			}
		};

		// Use explicit Callable for type safety
		this.future = executor.submit(new Callable<T>() {
			@Override
			public T call() throws Exception {
				hasStarted = true;
				try {
					T value = taskSupplier.get();
					result = value;
					hasFinished = true;
					return value;
				} catch (Throwable e) {
					exception = e;
					hasFinished = true;
					throw new RuntimeException(e);
				}
			}
		});
	}

	/**
	 * Creates a new SinglePipe from a Runnable.
	 * This pipe will return null when awaited.
	 *
	 * @param runnable the runnable to execute
	 */
	@SuppressWarnings("unchecked")
	public SinglePipe(Runnable runnable) {
		this(VirtualThreadExecutor.getExecutor(), runnable);
	}

	/**
	 * Creates a new SinglePipe from a Runnable using the specified executor.
	 * This pipe will return null when awaited.
	 *
	 * @param executor the executor service to use
	 * @param runnable the runnable to execute
	 */
	@SuppressWarnings("unchecked")
	public SinglePipe(ExecutorService executor, Runnable runnable) {
		Objects.requireNonNull(runnable, "Runnable cannot be null");
		this.executor = Objects.requireNonNull(executor, "Executor cannot be null");
		this.taskSupplier = () -> {
			runnable.run();
			return null;
		};

		// Use the appropriate submit method for Runnable
		this.future = executor.submit(new Runnable() {
			@Override
			public void run() {
				hasStarted = true;
				try {
					runnable.run();
					result = null;
					hasFinished = true;
				} catch (Throwable e) {
					exception = e;
					hasFinished = true;
					throw new RuntimeException(e);
				}
			}
		}, null);
	}

	/**
	 * Creates a new SinglePipe from a Consumer and an input value.
	 * This pipe will return null when awaited.
	 *
	 * @param <U>      the type of input to the consumer
	 * @param consumer the consumer to execute
	 * @param input    the input to provide to the consumer
	 */
	@SuppressWarnings("unchecked")
	public <U> SinglePipe(Consumer<U> consumer, U input) {
		this(VirtualThreadExecutor.getExecutor(), consumer, input);
	}

	/**
	 * Creates a new SinglePipe from a Consumer and an input value using the specified executor.
	 * This pipe will return null when awaited.
	 *
	 * @param <U>      the type of input to the consumer
	 * @param executor the executor service to use
	 * @param consumer the consumer to execute
	 * @param input    the input to provide to the consumer
	 */
	@SuppressWarnings("unchecked")
	public <U> SinglePipe(ExecutorService executor, Consumer<U> consumer, U input) {
		Objects.requireNonNull(consumer, "Consumer cannot be null");
		this.executor = Objects.requireNonNull(executor, "Executor cannot be null");
		this.taskSupplier = () -> {
			consumer.accept(input);
			return null;
		};

		// Use the appropriate submit method for Consumer
		this.future = executor.submit(new Callable<T>() {
			@Override
			public T call() throws Exception {
				hasStarted = true;
				try {
					consumer.accept(input);
					result = null;
					hasFinished = true;
					return null;
				} catch (Throwable e) {
					exception = e;
					hasFinished = true;
					throw new RuntimeException(e);
				}
			}
		});
	}

	@Override
	public T await() throws PipeException {
		try {
			return future.get();
		} catch (CancellationException e) {
			wasHalted = true;
			throw new PipeCancellationException("Pipe execution was cancelled", e);
		} catch (ExecutionException e) {
			return handleException(e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); // Preserve interrupt status
			throw new PipeException("Pipe execution was interrupted", e);
		}
	}

	@Override
	public boolean isCompleted() {
		return future.isDone() || hasFinished;
	}

	@Override
	public boolean isFailed() {
		return (future.isDone() && exception != null) || future.isCancelled();
	}

	@Override
	public boolean halt(boolean mayInterruptIfRunning) {
		boolean halted = future.cancel(mayInterruptIfRunning);
		if (halted) {
			wasHalted = true;
		}
		return halted;
	}

	@Override
	public boolean isHalted() {
		return wasHalted || future.isCancelled();
	}
}
