package com.sajal.VtPipe.example;

import com.sajal.VtPipe.core.Pipe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Advanced examples demonstrating more complex patterns with the VtPipe framework.
 */
public class AdvancedPipeExample {

	/**
	 * Runs several advanced pipe examples.
	 */
	public static void runAdvancedExamples() {
		parallelProcessingExample();
		dataTransformationPipeline();
		composingPipesExample();
		combinatorsExample();
	}

	/**
	 * Example showing parallel processing with multiple pipes.
	 */
	private static void parallelProcessingExample() {
		System.out.println("\n=== Parallel Processing Example ===");

		// Create several pipes for parallel execution
		Pipe<Integer> pipe1 = Pipe.flowAsync(() -> {
			sleep(500);
			System.out.println("Pipe 1 executing on thread: " + Thread.currentThread().getName());
			return 10;
		});

		Pipe<Integer> pipe2 = Pipe.flowAsync(() -> {
			sleep(300);
			System.out.println("Pipe 2 executing on thread: " + Thread.currentThread().getName());
			return 20;
		});

		Pipe<Integer> pipe3 = Pipe.flowAsync(() -> {
			sleep(700);
			System.out.println("Pipe 3 executing on thread: " + Thread.currentThread().getName());
			return 30;
		});

		// Wait for all pipes to complete
		System.out.println("Waiting for all pipes to complete...");
		Pipe<Void> allDone = Pipe.all(pipe1, pipe2, pipe3);
		long startTime = System.currentTimeMillis();
		allDone.await();
		long endTime = System.currentTimeMillis();

		// Now we can get the results immediately since all pipes are completed
		int sum = pipe1.await() + pipe2.await() + pipe3.await();
		System.out.println("Sum of results: " + sum);
		System.out.println("Total time: " + (endTime - startTime) + "ms");

		// Demonstrating Pipe.any()
		System.out.println("\nWaiting for the first pipe to complete...");
		Pipe<Integer> pipe4 = Pipe.flowAsync(() -> {
			sleep(800);
			return 40;
		});

		Pipe<Integer> pipe5 = Pipe.flowAsync(() -> {
			sleep(400);
			return 50;
		});

		Pipe<Integer> pipe6 = Pipe.flowAsync(() -> {
			sleep(600);
			return 60;
		});

		// Get the result of whichever pipe completes first
		startTime = System.currentTimeMillis();
		Integer firstResult = Pipe.any(pipe4, pipe5, pipe6).await();
		endTime = System.currentTimeMillis();

		System.out.println("First result: " + firstResult);
		System.out.println("Time to first result: " + (endTime - startTime) + "ms");
	}

	/**
	 * Example showing a data processing pipeline with pipes.
	 */
	private static void dataTransformationPipeline() {
		System.out.println("\n=== Data Transformation Pipeline Example ===");

		// Model a data processing pipeline with pipes
		List<String> rawData = List.of(
				"10,apple,red",
				"5,banana,yellow",
				"8,orange,orange",
				"3,grape,purple",
				"12,watermelon,green"
		);

		// Process each item in parallel
		List<Pipe<ProcessedItem>> processingPipes = rawData.stream()
				.map(rawLine -> Pipe.flowAsync(() -> {
					// Simulate processing delay
					sleep(200);
					String[] parts = rawLine.split(",");
					int quantity = Integer.parseInt(parts[0]);
					String name = parts[1];
					String color = parts[2];
					return new ProcessedItem(name, quantity, color);
				}).mapAsync(item -> {
					// Add a property
					sleep(100);
					item.totalValue = item.quantity * getPriceForFruit(item.name);
					return item;
				}))
				.collect(Collectors.toList());

		// Wait for all processing to complete
		System.out.println("Processing items...");
		long startTime = System.currentTimeMillis();
		Pipe.all(processingPipes.toArray(new Pipe[0])).await();
		long endTime = System.currentTimeMillis();

		// Collect and display results
		List<ProcessedItem> processedItems = processingPipes.stream()
				.map(Pipe::await)
				.collect(Collectors.toList());

		System.out.println("Processed " + processedItems.size() + " items in "
				+ (endTime - startTime) + "ms");

		double totalValue = processedItems.stream()
				.mapToDouble(item -> item.totalValue)
				.sum();

		System.out.println("Total inventory value: $" + String.format("%.2f", totalValue));

		for (ProcessedItem item : processedItems) {
			System.out.println(item);
		}
	}

	/**
	 * Example showing pipe composition.
	 */
	private static void composingPipesExample() {
		System.out.println("\n=== Pipe Composition Example ===");

		// Create a pipe that loads a user ID
		Pipe<Integer> userIdPipe = Pipe.flowAsync(() -> {
			sleep(300);
			System.out.println("Getting user ID...");
			return 42;
		});

		// Then use that ID to fetch user data
		Pipe<UserData> userDataPipe = userIdPipe.flatMapAsync(userId -> {
			System.out.println("Fetching data for user " + userId);
			return Pipe.flowAsync(() -> {
				sleep(500);
				return new UserData(userId, "John Doe", "john@example.com");
			});
		});

		// Then use the user data to fetch order history
		Pipe<List<Order>> orderHistoryPipe = userDataPipe.flatMapAsync(userData -> {
			System.out.println("Fetching orders for " + userData.name);
			return Pipe.flowAsync(() -> {
				sleep(600);
				List<Order> orders = new ArrayList<>();
				orders.add(new Order(1001, userData.id, 99.95));
				orders.add(new Order(1002, userData.id, 45.50));
				orders.add(new Order(1003, userData.id, 145.00));
				return orders;
			});
		});

		// Calculate total spending
		Pipe<Double> totalSpendingPipe = orderHistoryPipe.mapAsync(orders -> {
			return orders.stream()
					.mapToDouble(order -> order.amount)
					.sum();
		});

		double totalSpending = totalSpendingPipe.await();
		System.out.println("Total spending: $" + String.format("%.2f", totalSpending));
	}

	/**
	 * Example showing pipe combinators.
	 */
	private static void combinatorsExample() {
		System.out.println("\n=== Pipe Combinators Example ===");

		// Three independent data sources
		Pipe<String> userPipe = Pipe.flowAsync(() -> {
			sleep(400);
			return "User: John Doe";
		});

		Pipe<String> settingsPipe = Pipe.flowAsync(() -> {
			sleep(600);
			return "Settings: Dark Mode, Compact View";
		});

		Pipe<String> statsPipe = Pipe.flowAsync(() -> {
			sleep(300);
			return "Stats: Login Count=42, Last Login=2025-06-15";
		});

		// Example of combining results after all pipes complete
		System.out.println("Combining results after all pipes complete...");
		long startTime = System.currentTimeMillis();

		// Wait for all pipes to complete
		Pipe.all(userPipe, settingsPipe, statsPipe).await();

		// Now we can combine the results without blocking
		String user = userPipe.await();
		String settings = settingsPipe.await();
		String stats = statsPipe.await();

		String combinedData = String.join("\n", user, settings, stats);

		long endTime = System.currentTimeMillis();
		System.out.println("Combined data:\n" + combinedData);
		System.out.println("Total time: " + (endTime - startTime) + "ms");

		// Example of a timeout with a fallback
		System.out.println("\nDemonstrating timeout with fallback...");
		Pipe<String> verySlowPipe = Pipe.flowAsync(() -> {
			sleep(2000);
			return "This should timeout";
		});

		Pipe<String> timeoutPipe = Pipe.delay(500, TimeUnit.MILLISECONDS)
				.mapAsync(ignored -> "Timeout occurred");

		// Whichever completes first will be used
		String timeoutResult = Pipe.any(verySlowPipe, timeoutPipe).await();
		System.out.println("Result: " + timeoutResult);
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

	/**
	 * Helper to get a price for a fruit.
	 */
	private static double getPriceForFruit(String fruit) {
		switch (fruit.toLowerCase()) {
			case "apple":
				return 0.50;
			case "banana":
				return 0.25;
			case "orange":
				return 0.75;
			case "grape":
				return 2.00;
			case "watermelon":
				return 5.00;
			default:
				return 1.00;
		}
	}

	/**
	 * Main entry point for running the examples.
	 */
	public static void main(String[] args) {
		runAdvancedExamples();
	}

	/**
	 * Simple data class for a processed inventory item.
	 */
	private static class ProcessedItem {
		private final String name;
		private final int quantity;
		private final String color;
		private double totalValue;

		public ProcessedItem(String name, int quantity, String color) {
			this.name = name;
			this.quantity = quantity;
			this.color = color;
		}

		@Override
		public String toString() {
			return String.format("%s (qty: %d, color: %s) - $%.2f",
					name, quantity, color, totalValue);
		}
	}

	/**
	 * Simple data class for user data.
	 */
	private static class UserData {
		private final int id;
		private final String name;
		private final String email;

		public UserData(int id, String name, String email) {
			this.id = id;
			this.name = name;
			this.email = email;
		}
	}

	/**
	 * Simple data class for an order.
	 */
	private static class Order {
		private final int id;
		private final int userId;
		private final double amount;

		public Order(int id, int userId, double amount) {
			this.id = id;
			this.userId = userId;
			this.amount = amount;
		}
	}
}
