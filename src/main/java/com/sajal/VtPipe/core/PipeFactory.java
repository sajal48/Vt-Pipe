package com.sajal.VtPipe.core;

import com.sajal.VtPipe.core.impl.CompletedPipe;
import com.sajal.VtPipe.core.impl.FailedPipe;
import com.sajal.VtPipe.core.impl.SinglePipe;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Factory class for creating different types of Pipes.
 * This class is not meant to be instantiated directly.
 */
class PipeFactory {

	private PipeFactory() {
		// Prevent instantiation
	}

	/**
	 * Creates a new Pipe that is already completed with the given result.
	 *
	 * @param <T>    the type of the result
	 * @param result the result value
	 * @return a new pipe that is already completed
	 */
	static <T> Pipe<T> completed(T result) {
		return new CompletedPipe<>(result);
	}

	/**
	 * Creates a new Pipe that is already completed exceptionally with the given exception.
	 *
	 * @param <T>       the type of the result
	 * @param exception the exception that caused the failure
	 * @return a new pipe that is already completed exceptionally
	 */
	static <T> Pipe<T> failed(Throwable exception) {
		return new FailedPipe<>(exception);
	}

	/**
	 * Creates a new Pipe from a Supplier.
	 *
	 * @param <T>      the type of the result
	 * @param supplier the supplier that will provide the result
	 * @return a new pipe that will execute the supplier in a virtual thread
	 */
	static <T> Pipe<T> flowAsync(Supplier<T> supplier) {
		return new SinglePipe<>(supplier);
	}

	/**
	 * Creates a new Pipe from a Callable.
	 *
	 * @param <T>      the type of the result
	 * @param callable the callable that will provide the result
	 * @return a new pipe that will execute the callable in a virtual thread
	 */
	static <T> Pipe<T> callAsync(Callable<T> callable) {
		return new SinglePipe<>(callable);
	}

	/**
	 * Creates a new Pipe from a Runnable.
	 *
	 * @param runnable the runnable to execute
	 * @return a new pipe that will execute the runnable in a virtual thread
	 */
	static Pipe<Void> execAsync(Runnable runnable) {
		return new SinglePipe<>(runnable);
	}

	/**
	 * Creates a new Pipe with a Consumer.
	 *
	 * @param <T>      the type of input to the consumer
	 * @param consumer the consumer to execute
	 * @param input    the input to pass to the consumer
	 * @return a new pipe that will execute the consumer in a virtual thread
	 */
	static <T> Pipe<Void> processAsync(Consumer<T> consumer, T input) {
		return new SinglePipe<>(consumer, input);
	}

	/**
	 * Returns a new Pipe that is completed when all of the given Pipes complete.
	 *
	 * @param <T>   the type of the values from the input pipes
	 * @param pipes the pipes to combine
	 * @return a new pipe that is completed when all of the given pipes complete
	 */
	@SafeVarargs
	static <T> Pipe<Void> all(Pipe<? extends T>... pipes) {
		if (pipes.length == 0) {
			return completed(null);
		}

		return execAsync(() -> {
			for (Pipe<?> pipe : pipes) {
				pipe.await();
			}
		});
	}

	/**
	 * Returns a new Pipe that is completed when any of the given Pipes complete.
	 *
	 * @param <T>   the type of the values from the input pipes
	 * @param pipes the pipes to combine
	 * @return a new pipe that is completed when any of the given pipes complete
	 */
	@SafeVarargs
	static <T> Pipe<T> any(Pipe<? extends T>... pipes) {
		if (pipes.length == 0) {
			throw new IllegalArgumentException("At least one pipe must be provided");
		}

		if (pipes.length == 1) {
			@SuppressWarnings("unchecked")
			Pipe<T> result = (Pipe<T>) pipes[0];
			return result;
		}

		// Create a new pipe that waits for any of the input pipes to complete
		return callAsync(() -> {
			// Wait for any pipe to complete
			while (true) {
				for (Pipe<? extends T> pipe : pipes) {
					if (pipe.isCompleted()) {
						try {
							return pipe.await();
						} catch (Exception e) {
							// If one pipe fails, check if any other pipe has completed
							boolean allFailed = true;
							for (Pipe<? extends T> otherPipe : pipes) {
								if (!otherPipe.isFailed() && otherPipe.isCompleted()) {
									return otherPipe.await();
								}
								if (!otherPipe.isCompleted()) {
									allFailed = false;
								}
							}
							if (allFailed) {
								// All pipes have failed, rethrow the exception
								throw e;
							}
						}
					}
				}
				// Sleep briefly to avoid busy-waiting
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException("Interrupted while waiting for pipes to complete", e);
				}
			}
		});
	}

	/**
	 * Returns a new Pipe that is completed after the given delay.
	 *
	 * @param delay the delay after which the returned pipe is completed
	 * @param unit  the time unit of the delay
	 * @return a new pipe that will be completed after the given delay
	 */
	static Pipe<Void> delay(long delay, TimeUnit unit) {
		return execAsync(() -> {
			try {
				unit.sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Interrupted while sleeping", e);
			}
		});
	}
}
