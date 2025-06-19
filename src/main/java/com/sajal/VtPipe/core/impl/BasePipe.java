package com.sajal.VtPipe.core.impl;

import com.sajal.VtPipe.core.FallbackStrategy;
import com.sajal.VtPipe.core.Pipe;
import com.sajal.VtPipe.core.exception.PipeException;
import com.sajal.VtPipe.core.exception.PipeTimeoutException;
import com.sajal.VtPipe.core.executor.VirtualThreadExecutor;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/**
 * Base abstract implementation of the Pipe interface that provides
 * common functionality for all pipe implementations.
 *
 * @param <T> the type of result produced by this pipe
 */
public abstract class BasePipe<T> implements Pipe<T> {
	protected FallbackStrategy fallbackStrategy = FallbackStrategy.THROW_ERROR;
	protected T fallbackValue = null;
	protected ExecutorService executor = VirtualThreadExecutor.getExecutor();

	/**
	 * Executes this pipe and returns its result when complete.
	 * This will block the calling thread until completion.
	 *
	 * @return the result of the pipe execution
	 * @throws PipeException if the pipe execution fails
	 */
	@Override
	public abstract T await() throws PipeException;

	/**
	 * Executes this pipe and returns its result when complete, or times out after the specified duration.
	 *
	 * @param timeout the maximum time to wait
	 * @return the result of the pipe execution
	 * @throws PipeException        if the pipe execution fails
	 * @throws PipeTimeoutException if the timeout is reached
	 */
	@Override
	public T await(Duration timeout) throws PipeException {
		Pipe<T> timeoutPipe = this;

		if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
			// Create a timeout detector
			Pipe<Boolean> timeoutDetector = Pipe.delay(timeout.toMillis(), TimeUnit.MILLISECONDS)
					.mapAsync(ignored -> {
						if (!isCompleted()) {
							halt(true);
							return true; // Timeout occurred
						}
						return false; // No timeout
					});

			try {
				// Wait for either the pipe to complete or timeout to occur
				if (Boolean.TRUE.equals(timeoutDetector.await())) {
					// Timeout occurred
					throw new PipeTimeoutException("Pipe execution timed out after " + timeout);
				}

				return await();
			} finally {
				// Cancel the timeout detector if the pipe completes first
				timeoutDetector.halt(true);
			}
		}

		// No timeout specified, just await normally
		return await();
	}


	/**
	 * Default implementation of mapAsync that creates a new pipe that
	 * applies the given function to the result of this pipe in a new virtual thread.
	 */
	@Override
	public <U> Pipe<U> mapAsync(Function<? super T, ? extends U> fn) {
		Objects.requireNonNull(fn, "Function cannot be null");
		Supplier<U> supplier = () -> {
			T result = this.await();
			return fn.apply(result);
		};
		return new SinglePipe<>(executor, supplier);
	}


	/**
	 * Default implementation of consumeAsync that creates a new pipe that
	 * executes the given action with the result of this pipe in a new virtual thread.
	 */
	@Override
	public Pipe<Void> consumeAsync(Consumer<? super T> action) {
		Objects.requireNonNull(action, "Consumer cannot be null");
		Supplier<Void> supplier = () -> {
			action.accept(this.await());
			return null;
		};
		return new SinglePipe<>(executor, supplier);
	}


	/**
	 * Default implementation of thenAsync that creates a new pipe that
	 * executes the given action after this pipe completes in a new virtual thread.
	 */
	@Override
	public Pipe<Void> thenAsync(Runnable action) {
		Objects.requireNonNull(action, "Runnable cannot be null");
		Supplier<Void> supplier = () -> {
			this.await();
			action.run();
			return null;
		};
		return new SinglePipe<>(executor, supplier);
	}


	/**
	 * Default implementation of flatMapAsync that creates a new pipe that
	 * executes the given function with the result of this pipe in a new virtual thread.
	 */
	@Override
	public <U> Pipe<U> flatMapAsync(Function<? super T, ? extends Pipe<U>> fn) {
		Objects.requireNonNull(fn, "Function cannot be null");
		Supplier<U> supplier = () -> {
			T result = this.await();
			return fn.apply(result).await();
		};
		return new SinglePipe<>(executor, supplier);
	}

	/**
	 * Default implementation of recover that creates a new pipe that
	 * handles exceptions from this pipe by returning an alternative result.
	 */
	@Override
	public Pipe<T> recover(Function<Throwable, ? extends T> fn) {
		Objects.requireNonNull(fn, "Function cannot be null");
		return this.newPipe(() -> {
			try {
				return this.await();
			} catch (Throwable e) {
				return fn.apply(e);
			}
		});
	}

	/**
	 * Default implementation of orElse that creates a new pipe that
	 * returns the given fallback value if this pipe fails.
	 */
	@Override
	public Pipe<T> orElse(T fallback) {
		return withRecoveryStrategy(FallbackStrategy.RETURN_FALLBACK, fallback);
	}

	/**
	 * Default implementation of withRecoveryStrategy that creates a new pipe with
	 * the specified fallback strategy.
	 */
	@Override
	public Pipe<T> withRecoveryStrategy(FallbackStrategy strategy, T fallback) {
		SinglePipe<T> pipe = this.newPipe(() -> this.await());
		pipe.fallbackStrategy = Objects.requireNonNull(strategy, "Strategy cannot be null");
		pipe.fallbackValue = fallback;
		return pipe;
	}

	/**
	 * Default implementation of onComplete that creates a new pipe that
	 * executes the given action when this pipe completes.
	 */
	@Override
	public Pipe<T> onComplete(BiConsumer<? super T, ? super Throwable> action) {
		Objects.requireNonNull(action, "BiConsumer cannot be null");
		return this.newPipe(() -> {
			T result = null;
			Throwable exception = null;
			try {
				result = this.await();
			} catch (PipeException e) {
				exception = e.getCause() != null ? e.getCause() : e;
			}

			try {
				action.accept(result, exception);
			} catch (Throwable e) {
				if (exception != null) {
					// If the action throws an exception and there was already an exception,
					// the original exception is more important
					throw new PipeException(exception);
				}
				throw new PipeException(e);
			}

			if (exception != null) {
				throw new PipeException(exception);
			}

			return result;
		});
	}

	/**
	 * Default implementation of transform that creates a new pipe that
	 * executes the given function when this pipe completes.
	 */
	@Override
	public <U> Pipe<U> transform(BiFunction<? super T, Throwable, ? extends U> fn) {
		Objects.requireNonNull(fn, "BiFunction cannot be null");
		return this.newPipe(() -> {
			T result = null;
			Throwable exception = null;
			try {
				result = this.await();
			} catch (PipeException e) {
				exception = e.getCause() != null ? e.getCause() : e;
			}

			return fn.apply(result, exception);
		});
	}

	/**
	 * Helper method that handles execution exceptions based on the fallback strategy.
	 *
	 * @param exception the exception that occurred during execution
	 * @return the fallback value if strategy is RETURN_FALLBACK
	 * @throws PipeException if strategy is THROW_ERROR
	 */
	protected T handleException(Throwable exception) {
		switch (fallbackStrategy) {
			case THROW_ERROR:
				if (exception instanceof RuntimeException) {
					throw (RuntimeException) exception;
				} else if (exception instanceof Error) {
					throw (Error) exception;
				} else {
					throw new PipeException(exception.getMessage(), exception);
				}
			case SUPPRESS_ERROR:
				return null;
			case RETURN_FALLBACK:
				return fallbackValue;
			default:
				throw new IllegalStateException("Unknown fallback strategy: " + fallbackStrategy);
		}
	}

	/**
	 * Creates a new SinglePipe that uses the given supplier as the source of the result.
	 *
	 * @param <U>      the type of the result
	 * @param supplier the supplier that will provide the result
	 * @return a new SinglePipe that will execute the supplier
	 */
	protected <U> SinglePipe<U> newPipe(Supplier<U> supplier) {
		SinglePipe<U> pipe = new SinglePipe<>(supplier);
		pipe.fallbackStrategy = this.fallbackStrategy;
		pipe.fallbackValue = null; // Cannot transfer the fallback value as types might differ
		return pipe;
	}
}
