package com.sajal.VtPipe;

import com.sajal.VtPipe.core.Pipe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Advanced Performance Tests for VtPipe")
@Tag("performance")
public class AdvancedPerformanceTest {

	// Perform the specified workload
	private int performWorkload(WorkloadType type, int intensity) throws InterruptedException {
		switch (type) {
			case CPU_BOUND:
				// Simulate CPU-bound work with a complex calculation
				int result = 0;
				for (int i = 0; i < intensity * 100000; i++) {
					result += (i * 17) % 255;
				}
				return result;

			case IO_BOUND:
				// Simulate IO-bound work with sleep
				Thread.sleep(intensity);
				return intensity;

			case MIXED:
				// Mix of CPU and IO work
				int mixedResult = 0;
				for (int i = 0; i < intensity * 10000; i++) {
					mixedResult += (i * 17) % 255;
				}
				Thread.sleep(intensity / 2);
				return mixedResult;

			default:
				throw new IllegalArgumentException("Unknown workload type");
		}
	}

	@ParameterizedTest(name = "Thread scaling with {0} tasks")
	@ValueSource(ints = {10, 100, 1000})
	@Tag("performance")
	void threadScalingTest(int taskCount) {
		System.out.println("\n=== Thread Scaling Test with " + taskCount + " tasks ===");

		// Test parameters
		WorkloadType workloadType = WorkloadType.IO_BOUND;
		int workloadIntensity = 10; // milliseconds per task

		// Measure VtPipe performance
		long vtPipeStart = System.currentTimeMillis();

		List<Pipe<Integer>> pipes = new ArrayList<>();
		for (int i = 0; i < taskCount; i++) {
			final int taskId = i;
			pipes.add(Pipe.flowAsync(() -> {
				try {
					return performWorkload(workloadType, workloadIntensity);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				}
			}));
		}

		// Wait for all tasks
		Pipe<Void> allPipe = Pipe.all(pipes.toArray(new Pipe[0]));
		allPipe.await();

		long vtPipeDuration = System.currentTimeMillis() - vtPipeStart;

		// Measure ExecutorService with Virtual Threads performance
		long executorStart = System.currentTimeMillis();

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<Integer>> futures = new ArrayList<>();

			for (int i = 0; i < taskCount; i++) {
				final int taskId = i;
				futures.add(executor.submit(() -> {
					try {
						return performWorkload(workloadType, workloadIntensity);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new RuntimeException(e);
					}
				}));
			}

			// Wait for all tasks
			for (Future<Integer> future : futures) {
				future.get();
			}
		} catch (Exception e) {
			fail("ExecutorService test failed", e);
		}

		long executorDuration = System.currentTimeMillis() - executorStart;

		// Output results
		System.out.println("VtPipe completed " + taskCount + " tasks in: " + vtPipeDuration + " ms");
		System.out.println("Executor completed " + taskCount + " tasks in: " + executorDuration + " ms");

		// Calculate theoretical optimal time (with perfect parallelism)
		int availableProcessors = Runtime.getRuntime().availableProcessors();
		long theoreticalOptimalTime = (long) Math.ceil((double) taskCount * workloadIntensity / availableProcessors);

		// For IO-bound tasks, we can do much better than the processor count would suggest
		// because virtual threads don't block OS threads during IO
		if (workloadType == WorkloadType.IO_BOUND) {
			theoreticalOptimalTime = workloadIntensity; // Could execute all in parallel
		}

		System.out.println("Theoretical optimal time: ~" + theoreticalOptimalTime + " ms");
		System.out.println("VtPipe overhead factor: " + String.format("%.2f", (double) vtPipeDuration / theoreticalOptimalTime));
		System.out.println("Executor overhead factor: " + String.format("%.2f", (double) executorDuration / theoreticalOptimalTime));

		// We don't make strict assertions about performance, as it varies by environment
		// This is a benchmark, not a functional test
	}

	@Test
	@DisplayName("Pipe depth performance test")
	@Tag("performance")
	void pipeDepthPerformanceTest() {
		System.out.println("\n=== Pipe Depth Performance Test ===");

		// Test with different chain depths
		int[] chainDepths = {1, 10, 50, 100};

		for (int depth : chainDepths) {
			long startTime = System.currentTimeMillis();

			// Create a deeply chained pipe
			Pipe<Integer> pipe = Pipe.completed(0);

			for (int i = 0; i < depth; i++) {
				final int step = i;
				pipe = pipe.mapAsync(value -> value + 1);
			}

			// Execute the chain
			int result = pipe.await();

			long duration = System.currentTimeMillis() - startTime;

			System.out.println("Chain depth " + depth + " completed in " + duration +
					" ms with result " + result);

			// Verify the result
			assertEquals(depth, result, "Result should match the chain depth");
		}
	}

	@Test
	@DisplayName("Concurrent error handling performance")
	@Tag("performance")
	void concurrentErrorHandlingPerformance() {
		System.out.println("\n=== Concurrent Error Handling Performance ===");

		// Test parameters
		int taskCount = 1000;
		double failureRate = 0.1; // 10% of tasks will fail

		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		AtomicInteger recoveryCount = new AtomicInteger(0);

		List<Pipe<String>> pipes = new ArrayList<>();

		// Create many pipes with random failures
		for (int i = 0; i < taskCount; i++) {
			final int taskId = i;

			pipes.add(Pipe.flowAsync(() -> {
				try {
					// Small delay to simulate work
					Thread.sleep(5);

					// Random failures
					if (Math.random() < failureRate) {
						failureCount.incrementAndGet();
						throw new RuntimeException("Task " + taskId + " failed");
					}

					successCount.incrementAndGet();
					return "Task " + taskId + " succeeded";
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				}
			}).recover(ex -> {
				recoveryCount.incrementAndGet();
				return "Task " + taskId + " recovered";
			}));
		}

		// Wait for all to complete
		List<String> results = new ArrayList<>();
		for (Pipe<String> pipe : pipes) {
			results.add(pipe.await());
		}

		long duration = System.currentTimeMillis() - startTime;

		System.out.println("Processed " + taskCount + " tasks with " +
				failureCount.get() + " failures in " + duration + " ms");
		System.out.println("Success count: " + successCount.get());
		System.out.println("Recovery count: " + recoveryCount.get());

		// Verify results
		assertEquals(taskCount, results.size(), "Should have results for all tasks");
		assertEquals(failureCount.get(), recoveryCount.get(), "All failures should be recovered");
		assertTrue(failureCount.get() > 0, "Some tasks should have failed");
		assertTrue(successCount.get() > 0, "Some tasks should have succeeded");
	}

	@Test
	@DisplayName("Backpressure handling test")
	@Tag("performance")
	void backpressureHandlingTest() {
		System.out.println("\n=== Backpressure Handling Test ===");

		// Test a producer-consumer scenario with backpressure
		int producerCount = 10;
		int itemsPerProducer = 500;
		int totalItems = producerCount * itemsPerProducer;
		int maxQueueSize = 100; // Limit concurrent processing

		// Use a semaphore to limit concurrent processing (backpressure)
		Semaphore semaphore = new Semaphore(maxQueueSize);
		CountDownLatch completionLatch = new CountDownLatch(totalItems);
		AtomicInteger activeCount = new AtomicInteger(0);
		AtomicInteger maxConcurrent = new AtomicInteger(0);
		AtomicInteger completedCount = new AtomicInteger(0);

		long startTime = System.currentTimeMillis();

		// Create producers
		List<Pipe<Void>> producers = new ArrayList<>();

		for (int p = 0; p < producerCount; p++) {
			final int producerId = p;
			producers.add(Pipe.flowAsync(() -> {
				// Each producer creates many items
				for (int i = 0; i < itemsPerProducer; i++) {
					final int itemId = (producerId * itemsPerProducer) + i;

					// Apply backpressure - wait for permit before producing
					try {
						semaphore.acquire();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new RuntimeException(e);
					}

					// Process item asynchronously
					Pipe.flowAsync(() -> {
						try {
							// Track concurrency
							int current = activeCount.incrementAndGet();
							maxConcurrent.updateAndGet(max -> Math.max(max, current));

							// Process with variable duration
							Thread.sleep(1 + (itemId % 5));
							completedCount.incrementAndGet();

							return itemId;
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							throw new RuntimeException(e);
						} finally {
							activeCount.decrementAndGet();
							completionLatch.countDown();
							semaphore.release(); // Release permit when done
						}
					});
				}
				return null;
			}));
		}

		// Wait for all producers to finish submitting work
		Pipe<Void> allProducers = Pipe.all(producers.toArray(new Pipe[0]));
		allProducers.await();

		// Wait for all processing to complete
		try {
			boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
			assertTrue(completed, "All items should be processed within timeout");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			fail("Test interrupted");
		}

		long duration = System.currentTimeMillis() - startTime;

		System.out.println("Processed " + completedCount.get() + " items in " + duration + " ms");
		System.out.println("Maximum concurrent processing: " + maxConcurrent.get());

		// Verify results
		assertEquals(totalItems, completedCount.get(), "All items should be processed");
		assertTrue(maxConcurrent.get() <= maxQueueSize,
				"Should respect backpressure limit: " + maxConcurrent.get());
	}

	@Test
	@DisplayName("Memory efficiency test")
	@Tag("performance")
	void memoryEfficiencyTest() {
		System.out.println("\n=== Memory Efficiency Test ===");

		int iterations = 3;
		int tasksPerIteration = 100000;

		for (int iteration = 0; iteration < iterations; iteration++) {
			System.out.println("Iteration " + (iteration + 1) + " of " + iterations);

			// Force GC to get cleaner measurements
			System.gc();

			// Measure memory before
			long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

			// Create many pipes
			List<Pipe<Integer>> pipes = new ArrayList<>(tasksPerIteration);
			for (int i = 0; i < tasksPerIteration; i++) {
				final int taskId = i;
				pipes.add(Pipe.flowAsync(() -> {
					return taskId;
				}));
			}

			// Execute a subset to ensure pipes are created but not all are awaited
			for (int i = 0; i < tasksPerIteration / 10; i++) {
				pipes.get(i).await();
			}

			// Measure memory after
			long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
			long memoryUsed = memoryAfter - memoryBefore;

			System.out.println("Memory used for " + tasksPerIteration + " pipes: " +
					(memoryUsed / 1024 / 1024) + " MB");
			System.out.println("Average memory per pipe: " +
					(memoryUsed / tasksPerIteration) + " bytes");

			// Clear references to allow GC
			pipes.clear();
			System.gc();
		}

		// This is a benchmark, not a strict test
		// We're just monitoring memory usage patterns
	}

	// Test workload types
	private enum WorkloadType {
		CPU_BOUND,
		IO_BOUND,
		MIXED
	}
}
