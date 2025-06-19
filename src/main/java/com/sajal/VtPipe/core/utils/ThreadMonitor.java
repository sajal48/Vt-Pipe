package com.sajal.VtPipe.core.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Utility class for monitoring and debugging virtual threads.
 * Provides functionality to dump thread information and monitor thread usage.
 */
public final class ThreadMonitor {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	private ThreadMonitor() {
		// Utility class, no instances needed
	}

	/**
	 * Prints information about all virtual threads currently running.
	 */
	public static void dumpVirtualThreads() {
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);

		String virtualThreads = Arrays.stream(threadInfos)
				.filter(t -> t.getThreadName().contains("vt-pipe"))
				.map(ThreadMonitor::formatThreadInfo)
				.collect(Collectors.joining("\n"));

		System.out.println("=== Virtual Thread Dump at " +
				LocalDateTime.now().format(TIME_FORMATTER) + " ===");
		System.out.println(virtualThreads);
		System.out.println("=== End of Virtual Thread Dump ===");
	}

	/**
	 * Formats a ThreadInfo object into a readable string.
	 *
	 * @param info the ThreadInfo to format
	 * @return a formatted string representation of the thread
	 */
	private static String formatThreadInfo(ThreadInfo info) {
		StringBuilder sb = new StringBuilder();
		sb.append("\"").append(info.getThreadName()).append("\"")
				.append(" Id=").append(info.getThreadId())
				.append(" ").append(info.getThreadState());

		if (info.getLockName() != null) {
			sb.append(" on ").append(info.getLockName());
		}

		if (info.getLockOwnerName() != null) {
			sb.append(" owned by \"").append(info.getLockOwnerName())
					.append("\" Id=").append(info.getLockOwnerId());
		}

		if (info.isSuspended()) {
			sb.append(" (suspended)");
		}

		if (info.isInNative()) {
			sb.append(" (in native)");
		}

		return sb.toString();
	}

	/**
	 * Gracefully shuts down an executor service with timeout.
	 *
	 * @param executorService the executor service to shut down
	 * @param timeout         the maximum time to wait
	 * @param unit            the time unit of the timeout
	 * @return true if the executor was terminated, false if the timeout elapsed
	 */
	public static boolean shutdownExecutor(ExecutorService executorService,
	                                       long timeout,
	                                       TimeUnit unit) {
		if (executorService == null) {
			return true;
		}

		executorService.shutdown();
		try {
			if (!executorService.awaitTermination(timeout, unit)) {
				executorService.shutdownNow();
				return executorService.awaitTermination(timeout, unit);
			}
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			executorService.shutdownNow();
			return false;
		}
	}

	/**
	 * Returns the current thread name and indicates if it's a virtual thread.
	 *
	 * @return a string representing the current thread
	 */
	public static String getCurrentThreadInfo() {
		Thread current = Thread.currentThread();
		return "Thread[" + current.getName() +
				", " + (current.isVirtual() ? "virtual" : "platform") +
				", " + current.getState() + "]";
	}
}
