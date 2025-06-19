package com.sajal.VtPipe.examples;

import com.sajal.VtPipe.core.Pipe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating basic usage of the VtPipe library.
 */
public class PipeExample {

	public static void main(String[] args) {
		// Simple example of async execution
		basicExample();

		// Example of chaining operations
		chainingExample();

		// Example of parallel execution
		parallelExample();

		// Example of error handling
		errorHandlingExample();
	}

	private static void basicExample() {
		System.out.println("\n=== Basic Example ===");
		// Create an async pipe that performs work
		Pipe<String> pipe = Pipe.flowAsync(() -> {
			System.out.println("Running on thread: " + Thread.currentThread().getName());
			// Simulate work
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
			return "Task completed";
		});

		System.out.println("Pipe created, work is happening asynchronously...");

		// Wait for the result
		String result = pipe.await();
		System.out.println("Result: " + result);
	}

	private static void chainingExample() {
		System.out.println("\n=== Chaining Example ===");
		// Start with a simple value
		Pipe<Integer> pipe = Pipe.flowAsync(() -> {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
			return 5;
		});

		// Chain multiple transformations
		Pipe<String> result = pipe
				.mapAsync(value -> value * 2)                // Transform to 10
				.mapAsync(value -> value + 3)                // Transform to 13
				.flatMapAsync(value -> {
					// First delay, then map the final result
					final int finalValue = value; // Capture the value in a final variable
					return Pipe.delay(50, TimeUnit.MILLISECONDS)
							.mapAsync(ignored -> "Result: " + finalValue);
				});    // Add a delay then format

		System.out.println(result.await());
	}

	private static void parallelExample() {
		System.out.println("\n=== Parallel Execution Example ===");

		int taskCount = 5;
		List<Pipe<Integer>> pipes = new ArrayList<>();

		// Create several async tasks
		for (int i = 0; i < taskCount; i++) {
			final int taskId = i;
			pipes.add(Pipe.flowAsync(() -> {
				int sleepTime = (taskId + 1) * 50;
				System.out.println("Task " + taskId + " starting, will take " + sleepTime + "ms");
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				}
				System.out.println("Task " + taskId + " completed");
				return taskId;
			}));
		}

		// Create a pipe that waits for the first task to complete
		Pipe<Integer> firstDone = Pipe.any(pipes.toArray(new Pipe[0]));

		// Create a pipe that waits for all tasks to complete
		Pipe<Void> allDone = Pipe.all(pipes.toArray(new Pipe[0]));

		// Wait for first task to complete
		Integer firstResult = firstDone.await();
		System.out.println("First completed task: " + firstResult);

		// Wait for all tasks to complete
		allDone.await();
		System.out.println("All tasks completed");

		// Get all results
		List<Integer> results = new ArrayList<>();
		for (Pipe<Integer> pipe : pipes) {
			results.add(pipe.await());
		}

		System.out.println("Results: " + results);
	}

	private static void errorHandlingExample() {
		System.out.println("\n=== Error Handling Example ===");

		// Create a pipe that will fail
		Pipe<String> failingPipe = Pipe.flowAsync(() -> {
			System.out.println("About to throw an exception");
			throw new RuntimeException("Simulated failure");
		});

		// Handle the error using recover
		Pipe<String> recoveredPipe = failingPipe.recover(ex -> {
			System.out.println("Recovered from: " + ex.getMessage());
			return "Recovery value";
		});

		// Get the recovered result
		String result = recoveredPipe.await();
		System.out.println("Final result after recovery: " + result);

		// Another approach - handle exceptions with recover
		Pipe.flowAsync(() -> {
					throw new IllegalArgumentException("Another error");
				})
				.recover(ex -> {
					System.out.println("Handling exception: " + ex.getMessage());
					return "Fallback value";
				})
				.await();
	}
}
