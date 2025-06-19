package com.sajal.VtPipe.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class that provides a shared virtual thread executor service.
 * This class leverages Java's virtual threads feature for efficient concurrency.
 * Virtual threads are lightweight and designed for high-throughput, concurrent applications.
 */
public final class ExecutorUtil {

	/**
	 * Singleton instance of the virtual thread executor service.
	 * Configured to create a new virtual thread for each submitted task.
	 */
	private static final ExecutorService EXECUTOR = createExecutor();

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private ExecutorUtil() {
		// Utility class, no instances needed
	}

	/**
	 * Creates the virtual thread executor with named threads for better debugging.
	 *
	 * @return The configured ExecutorService using virtual threads
	 */
	private static ExecutorService createExecutor() {
		return Executors.newThreadPerTaskExecutor(
				Thread.ofVirtual()
						.name("vt-pipe-", 0)
						.factory());
	}

	/**
	 * Returns the shared executor service instance.
	 *
	 * @return The application's virtual thread executor service
	 */
	public static ExecutorService getExecutor() {
		return EXECUTOR;
	}
}