package com.sajal.VtPipe.core.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides virtual thread execution services for the VtPipe framework.
 * This class leverages Java's virtual threads feature for efficient concurrency.
 * Virtual threads are lightweight and designed for high-throughput, concurrent applications.
 */
public final class VirtualThreadExecutor {

	/**
	 * Singleton instance of the virtual thread executor service.
	 * Configured to create a new virtual thread for each submitted task.
	 */
	private static final ExecutorService DEFAULT_EXECUTOR = createExecutor("vt-pipe");

	/**
	 * Counter for named executor instances
	 */
	private static final AtomicInteger EXECUTOR_COUNTER = new AtomicInteger(0);

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private VirtualThreadExecutor() {
		// Utility class, no instances needed
	}

	/**
	 * Creates a virtual thread executor with named threads for better debugging.
	 *
	 * @param namePrefix The prefix to use for naming threads
	 * @return The configured ExecutorService using virtual threads
	 */
	public static ExecutorService createExecutor(String namePrefix) {
		ThreadFactory factory = Thread.ofVirtual()
				.name(namePrefix + "-", 0)
				.factory();
		return Executors.newThreadPerTaskExecutor(factory);
	}

	/**
	 * Creates a new dedicated virtual thread executor with a unique name.
	 *
	 * @return A new ExecutorService using virtual threads
	 */
	public static ExecutorService createDedicatedExecutor() {
		return createExecutor("vt-pipe-dedicated-" + EXECUTOR_COUNTER.incrementAndGet());
	}

	/**
	 * Returns the shared executor service instance.
	 *
	 * @return The application's default virtual thread executor service
	 */
	public static ExecutorService getExecutor() {
		return DEFAULT_EXECUTOR;
	}

	/**
	 * Executes a Runnable in a virtual thread.
	 *
	 * @param runnable The runnable to execute
	 */
	public static void execute(Runnable runnable) {
		DEFAULT_EXECUTOR.execute(runnable);
	}
}
