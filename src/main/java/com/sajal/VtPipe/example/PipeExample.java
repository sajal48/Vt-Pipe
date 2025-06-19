package com.sajal.VtPipe.example;

import com.sajal.VtPipe.core.Pipe;
import com.sajal.VtPipe.core.exception.PipeException;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating various uses of the Pipe framework.
 */
public class PipeExample {
	/**
	 * Runs several examples of using pipes.
	 */
	public static void runExamples() {
		basicPipeExample();
		chainingExample();
		fallbackExample();
		consumerExample();
		timeoutExample();
	}

	/**
	 * Basic example showing how to create and join a pipe.
	 */
	private static void basicPipeExample() {
		System.out.println("\n=== Basic Pipe Example ===");

		// Create a pipe that supplies a result
		Pipe<String> pipe = Pipe.flowAsync(() -> {
			sleep(500); // Simulate work
			System.out.println("Executing in thread: " + Thread.currentThread().getName());
			return "Hello from VT Pipe!";
		});

		// Wait for and get the result
		String result = pipe.await();
		System.out.println("Result: " + result);

		// Create a pipe with a runnable (returns void)
		Pipe<Void> voidPipe = Pipe.execAsync(() -> {
			sleep(300);
			System.out.println("Task completed in thread: " + Thread.currentThread().getName());
		});

		voidPipe.await();

		// Check pipe status
		System.out.println("Pipe is done: " + pipe.isCompleted());
	}

	/**
	 * Example showing how to chain operations with pipes.
	 */
	private static void chainingExample() {
		System.out.println("\n=== Chaining Example ===");

		// Create a pipeline of operations
		Pipe<Integer> pipe = Pipe.flowAsync(() -> {
			sleep(300);
			return 5;
		}).mapAsync(n -> {
			System.out.println("Doubling on thread: " + Thread.currentThread().getName());
			return n * 2;
		}).mapAsync(n -> {
			sleep(200);
			System.out.println("Squaring on thread: " + Thread.currentThread().getName());
			return n * n;
		}).mapAsync(n -> {
			System.out.println("Adding 1 on thread: " + Thread.currentThread().getName());
			return n + 1;
		});

		int result = pipe.await();
		System.out.println("Final result: " + result);  // Should be (5*2)^2 + 1 = 101
	}

	/**
	 * Example showing error handling with fallbacks.
	 */
	private static void fallbackExample() {
		System.out.println("\n=== Fallback Example ===");

		// Create a pipe that will fail
		Pipe<String> failingPipe = Pipe.flowAsync(() -> {
			sleep(300);
			if (true) { // Always throw for this example
				throw new RuntimeException("Simulated error in pipe");
			}
			return "This will never be returned";
		});

		// Handle the error with a fallback value
		String result1 = failingPipe.orElse("Fallback value").await();
		System.out.println("Result with fallback: " + result1);

		// Handle the error with exception mapping
		String result2 = failingPipe.recover(exception -> {
			return "Recovered with: " + exception.getMessage();
		}).await();
		System.out.println("Result with recover: " + result2);

		// Handle with transformation that processes both success and error cases
		String result3 = failingPipe.transform((value, exception) -> {
			if (exception != null) {
				return "Error occurred: " + exception.getMessage();
			} else {
				return "Success: " + value;
			}
		}).await();
		System.out.println("Result with transform: " + result3);
	}

	/**
	 * Example showing consumer operations with pipes.
	 */
	private static void consumerExample() {
		System.out.println("\n=== Consumer Example ===");

		// Create a pipe and consume its result
		Pipe<Void> pipe = Pipe.flowAsync(() -> {
			sleep(300);
			return "Hello, Consumer!";
		}).consumeAsync(result -> {
			System.out.println("Consumed on thread: " + Thread.currentThread().getName());
			System.out.println("Got result: " + result);
		});

		// Wait for the consumer to complete
		pipe.await();

		// You can also chain more operations after consuming
		Pipe<Integer> lengthPipe = Pipe.flowAsync(() -> {
			return "Measuring length";
		}).mapAsync(s -> {
			System.out.println("Processing: " + s);
			return s;
		}).mapAsync(String::length);

		int length = lengthPipe.await();
		System.out.println("Length: " + length);
	}

	/**
	 * Example showing timeout handling with pipes.
	 */
	private static void timeoutExample() {
		System.out.println("\n=== Timeout Example ===");

		// Create a slow pipe
		Pipe<String> slowPipe = Pipe.flowAsync(() -> {
			sleep(2000); // This operation is slow
			return "Finally done!";
		});

		try {
			// Try to get the result with a shorter timeout
			String result = slowPipe.await(Duration.ofMillis(500));
			System.out.println("Result: " + result);
		} catch (PipeException e) {
			System.out.println("Got timeout exception as expected: " + e.getMessage());
		}

		// Create a delayed operation using the delay method
		Pipe<Void> delayedPipe = Pipe.delay(1000, TimeUnit.MILLISECONDS).thenAsync(() -> {
			System.out.println("Executed after delay on thread: " + Thread.currentThread().getName());
		});

		System.out.println("Waiting for delayed operation...");
		delayedPipe.await();
		System.out.println("Delayed operation completed");
	}

	/**
	 * Helper to sleep for a specified number of milliseconds.
	 */
	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
