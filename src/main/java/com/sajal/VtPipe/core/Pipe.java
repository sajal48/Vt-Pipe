package com.sajal.VtPipe.core;

import com.sajal.VtPipe.core.exception.PipeException;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/**
 * Core interface for the Virtual Thread Pipeline.
 * A Pipe represents an asynchronous operation that may produce a result or just perform some action.
 * Inspired by CompletableFuture but designed specifically for virtual threads.
 *
 * @param <T> the type of result produced by this pipe
 */
public interface Pipe<T> {
	/**
	 * Factory method that creates a new completed Pipe with the given result.
	 * This is useful when you need a pre-completed pipe or for testing.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * // Create a pipe that's already done
	 * Pipe<UserData> userDataPipe = Pipe.completed(cachedUserData);
	 *
	 * // Can be used in a pipeline
	 * Pipe<String> usernamePipe = userDataPipe
	 *     .map(data -> data.username);
	 *
	 * // No waiting needed - result is available immediately
	 * String username = usernamePipe.await();
	 * }</pre>
	 *
	 * @param <U>    the type of the result
	 * @param result the result value
	 * @return a new pipe that is already completed with the given result
	 */
	static <U> Pipe<U> completed(U result) {
		return PipeFactory.completed(result);
	}

	/**
	 * Factory method that creates a new completed Pipe that failed with the given exception.
	 * This is useful for creating failure scenarios or propagating known errors.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * // Create a pipe that's already failed
	 * Pipe<OrderData> orderPipe;
	 *
	 * if (!isOrderValid) {
	 *     orderPipe = Pipe.failed(new ValidationException("Invalid order ID"));
	 * } else {
	 *     orderPipe = Pipe.flowAsync(() -> processOrder(orderId));
	 * }
	 *
	 * // Handle the error
	 * Pipe<OrderData> safePipe = orderPipe.recover(ex -> {
	 *     logError("Order processing failed", ex);
	 *     return OrderData.createEmptyOrder();
	 * });
	 * }</pre>
	 *
	 * @param <U>       the type of the result
	 * @param exception the exception that caused the failure
	 * @return a new pipe that is already completed exceptionally with the given exception
	 */
	static <U> Pipe<U> failed(Throwable exception) {
		return PipeFactory.failed(exception);
	}

	/**
	 * Factory method that creates a new Pipe from a Supplier.
	 * This is one of the most common ways to start a pipeline with an asynchronous operation.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * // Start an asynchronous operation
	 * Pipe<List<Customer>> customersPipe = Pipe.flowAsync(() -> {
	 *     System.out.println("Fetching customers on thread: " + Thread.currentThread().getName());
	 *     return databaseService.fetchAllCustomers();
	 * });
	 *
	 * // Chain more operations
	 * Pipe<Integer> countPipe = customersPipe.map(List::size);
	 *
	 * // Get final result
	 * int customerCount = countPipe.await();
	 * }</pre>
	 *
	 * @param <U>      the type of the result
	 * @param supplier the supplier that will provide the result
	 * @return a new pipe that will execute the supplier in a virtual thread
	 */
	static <U> Pipe<U> flowAsync(Supplier<U> supplier) {
		return PipeFactory.flowAsync(supplier);
	}

	/**
	 * Factory method that creates a new Pipe from a Callable.
	 * Useful when you have a task that might throw checked exceptions.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * // Execute a task that throws checked exceptions
	 * Pipe<FileData> filePipe = Pipe.callAsync(() -> {
	 *     try (InputStream is = new FileInputStream("data.txt")) {
	 *         byte[] data = is.readAllBytes();
	 *         return new FileData(data);
	 *     } // IOException will be wrapped in a PipeException
	 * });
	 *
	 * // Handle exceptions
	 * FileData data = filePipe
	 *     .recover(ex -> {
	 *         System.err.println("File read failed: " + ex.getMessage());
	 *         return FileData.empty();
	 *     })
	 *     .await();
	 * }</pre>
	 *
	 * @param <U>      the type of the result
	 * @param callable the callable that will provide the result
	 * @return a new pipe that will execute the callable in a virtual thread
	 */
	static <U> Pipe<U> callAsync(Callable<U> callable) {
		return PipeFactory.callAsync(callable);
	}

	/**
	 * Factory method that creates a new Pipe from a Runnable.
	 * Useful for fire-and-forget tasks or operations that don't return a result.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * // Execute a task that doesn't return a value
	 * Pipe<Void> loggingPipe = Pipe.execAsync(() -> {
	 *     System.out.println("Running on thread: " + Thread.currentThread().getName());
	 *     auditLogger.recordEvent("Operation started at " + System.currentTimeMillis());
	 * });
	 *
	 * // Wait for completion if needed
	 * loggingPipe.await();
	 *
	 * // Or chain with other operations
	 * Pipe<Void> notificationPipe = loggingPipe.then(() ->
	 *     notificationService.sendAlert("Operation in progress"));
	 * }</pre>
	 *
	 * @param runnable the runnable to execute
	 * @return a new pipe that will execute the runnable in a virtual thread
	 */
	static Pipe<Void> execAsync(Runnable runnable) {
		return PipeFactory.execAsync(runnable);
	}

	/**
	 * Factory method that creates a new Pipe with a Consumer.
	 * Useful when you need to process a known input value asynchronously.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * // Process an existing object asynchronously
	 * User user = getCurrentUser();
	 *
	 * Pipe<Void> processPipe = Pipe.processAsync(
	 *     u -> {
	 *         System.out.println("Processing user data for: " + u.getName());
	 *         userProcessor.processUserPreferences(u);
	 *         analyticsService.recordUserActivity(u);
	 *     },
	 *     user
	 * );
	 *
	 * // Continue execution without waiting
	 * System.out.println("Started user processing");
	 *
	 * // Later, wait for completion if needed
	 * processPipe.await();
	 * }</pre>
	 *
	 * @param <U>      the type of input to the consumer
	 * @param consumer the consumer to execute
	 * @param input    the input to pass to the consumer
	 * @return a new pipe that will execute the consumer in a virtual thread
	 */
	static <U> Pipe<Void> processAsync(Consumer<U> consumer, U input) {
		return PipeFactory.processAsync(consumer, input);
	}

	/**
	 * Returns a new Pipe that is completed when all of the given Pipes complete.
	 * If any of the given Pipes complete exceptionally, then the returned Pipe
	 * also completes exceptionally. This is useful for waiting on multiple independent
	 * operations to complete.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * // Start multiple asynchronous operations
	 * Pipe<UserData> userPipe = Pipe.flowAsync(() -> fetchUserData(userId));
	 * Pipe<List<Order>> ordersPipe = Pipe.flowAsync(() -> fetchUserOrders(userId));
	 * Pipe<CreditReport> creditPipe = Pipe.flowAsync(() -> fetchCreditReport(userId));
	 *
	 * // Wait for all operations to complete
	 * Pipe<Void> allDataPipe = Pipe.all(userPipe, ordersPipe, creditPipe);
	 *
	 * // This will block until all three operations complete
	 * allDataPipe.await();
	 *
	 * // Now we can safely use the results without blocking
	 * UserData user = userPipe.await(); // Will return immediately
	 * List<Order> orders = ordersPipe.await(); // Will return immediately
	 * CreditReport report = creditPipe.await(); // Will return immediately
	 * }</pre>
	 *
	 * @param <U>   the type of the values from the input pipes
	 * @param pipes the pipes to combine
	 * @return a new pipe that is completed when all of the given pipes complete
	 */
	@SafeVarargs
	static <U> Pipe<Void> all(Pipe<? extends U>... pipes) {
		return PipeFactory.all(pipes);
	}

	/**
	 * Returns a new Pipe that is completed when any of the given Pipes complete.
	 * If all of the given Pipes complete exceptionally, then the returned Pipe
	 * also completes exceptionally. This is useful for implementing timeout patterns
	 * or for getting the fastest result from multiple sources.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * // Try to fetch data from multiple redundant sources
	 * Pipe<DataResult> primary = Pipe.flowAsync(() -> fetchFromPrimarySource(query));
	 * Pipe<DataResult> secondary = Pipe.flowAsync(() -> fetchFromSecondarySource(query));
	 * Pipe<DataResult> tertiary = Pipe.flowAsync(() -> fetchFromTertiarySource(query));
	 *
	 * // Get result from whichever source responds first
	 * Pipe<DataResult> fastestSource = Pipe.any(primary, secondary, tertiary);
	 *
	 * // This will return as soon as any source completes
	 * DataResult result = fastestSource.await();
	 *
	 * // Example with timeout:
	 * Pipe<String> operation = Pipe.flowAsync(() -> performLongOperation());
	 * Pipe<Void> timeout = Pipe.delay(5000, TimeUnit.MILLISECONDS);
	 *
	 * try {
	 *     Object result = Pipe.any(operation, timeout).await();
	 *     if (result == null) {
	 *         System.out.println("Operation timed out");
	 *     } else {
	 *         System.out.println("Operation completed: " + result);
	 *     }
	 * } catch (PipeException e) {
	 *     System.out.println("Operation failed: " + e.getMessage());
	 * }
	 * }</pre>
	 *
	 * @param <U>   the type of the values from the input pipes
	 * @param pipes the pipes to combine
	 * @return a new pipe that is completed when any of the given pipes complete
	 */
	@SafeVarargs
	static <U> Pipe<U> any(Pipe<? extends U>... pipes) {
		return PipeFactory.any(pipes);
	}

	/**
	 * Returns a new Pipe that is completed after the given delay.
	 * This is useful for implementing timeouts, delayed execution,
	 * or periodic tasks.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * // Delay execution for 2 seconds
	 * Pipe<Void> delayPipe = Pipe.delay(2, TimeUnit.SECONDS);
	 *
	 * // Chain an action to execute after the delay
	 * Pipe<Void> delayedAction = delayPipe
	 *     .then(() -> System.out.println("Executed 2 seconds later"));
	 *
	 * // Main thread can continue doing other work
	 * System.out.println("Started delayed action");
	 *
	 * // Wait for the delayed action if needed
	 * delayedAction.await();
	 *
	 * // Can be used for implementing timeouts:
	 * Pipe<r> operation = Pipe.flowAsync(() -> performLongRunningTask());
	 *
	 * try {
	 *     // Wait at most 5 seconds for the result
	 *     Result result = operation.await(Duration.ofSeconds(5));
	 *     System.out.println("Got result: " + result);
	 * } catch (PipeTimeoutException e) {
	 *     System.out.println("Operation timed out");
	 * }
	 * }</pre>
	 *
	 * @param delay the delay after which the returned pipe is completed
	 * @param unit  the time unit of the delay
	 * @return a new pipe that will be completed after the given delay
	 */
	static Pipe<Void> delay(long delay, TimeUnit unit) {
		return PipeFactory.delay(delay, unit);
	}

	/**
	 * Executes this pipe and returns its result when complete.
	 * This will block the calling thread until completion.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * Pipe<String> pipe = Pipe.flowAsync(() -> "Hello, World!");
	 * String result = pipe.await(); // Blocks until the result is available
	 * }</pre>
	 *
	 * @return the result of the pipe execution
	 * @throws PipeException if the pipe execution fails
	 */
	T await() throws PipeException;

	/**
	 * Executes this pipe and returns its result when complete, or times out after the specified duration.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * Pipe<String> pipe = Pipe.flowAsync(() -> {
	 *     Thread.sleep(2000); // Simulating long operation
	 *     return "Result";
	 * });
	 *
	 * try {
	 *     String result = pipe.await(Duration.ofMillis(1000)); // Wait max 1 second
	 *     System.out.println("Got result: " + result);
	 * } catch (PipeTimeoutException e) {
	 *     System.out.println("Operation timed out");
	 * }
	 * }</pre>
	 *
	 * @param timeout the maximum time to wait
	 * @return the result of the pipe execution
	 * @throws PipeException                                        if the pipe execution fails
	 * @throws com.sajal.VtPipe.core.exception.PipeTimeoutException if the timeout is reached
	 */
	T await(Duration timeout) throws PipeException;

	/**
	 * Checks if this pipe has completed (either successfully or exceptionally).
	 *
	 * @return true if this pipe has completed
	 */
	boolean isCompleted();

	/**
	 * Checks if this pipe completed exceptionally.
	 *
	 * @return true if this pipe completed exceptionally
	 */
	boolean isFailed();

	/**
	 * Attempts to cancel execution of this pipe.
	 *
	 * @param mayInterruptIfRunning true if the thread executing this pipe should be interrupted
	 * @return true if this pipe was cancelled
	 */
	boolean halt(boolean mayInterruptIfRunning);

	/**
	 * Checks if this pipe was cancelled.
	 *
	 * @return true if this pipe was cancelled
	 */
	boolean isHalted();

	/**
	 * Creates a new pipe that executes the given function in a new virtual thread
	 * with the result of this pipe as input, once it completes.
	 * the computation happens asynchronously in a new virtual thread.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * Pipe<byte[]> pipe = Pipe.flowAsync(() -> readFileData("input.txt"));
	 *
	 * // Process data in a separate thread
	 * Pipe<String> processed = pipe.mapAsync(data -> {
	 *     System.out.println("Processing in thread: " + Thread.currentThread().getName());
	 *     return new String(data, StandardCharsets.UTF_8);
	 * });
	 *
	 * String result = processed.await();
	 * }</pre>
	 *
	 * @param <U> the type of the returned pipe's result
	 * @param fn  the function to apply to this pipe's result
	 * @return a new pipe that, when awaited, will return the result of applying the function
	 */
	<U> Pipe<U> mapAsync(Function<? super T, ? extends U> fn);

	/**
	 * Creates a new pipe that executes the given action in a new virtual thread
	 * with the result of this pipe as input, once it completes.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * Pipe<DataEvent> eventPipe = Pipe.flowAsync(() -> receiveEvent());
	 *
	 * // Process event asynchronously
	 * Pipe<Void> processedPipe = eventPipe.consumeAsync(event -> {
	 *     System.out.println("Processing in thread: " + Thread.currentThread().getName());
	 *     processEventData(event); // Potentially time-consuming operation
	 * });
	 *
	 * processedPipe.await(); // Wait for processing to complete
	 * }</pre>
	 *
	 * @param action the action to perform with this pipe's result
	 * @return a new pipe that, when awaited, will return null after executing the action
	 */
	Pipe<Void> consumeAsync(Consumer<? super T> action);

	/**
	 * Creates a new pipe that executes the given runnable in a new virtual thread
	 * when this pipe completes, regardless of the result of this pipe.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * Pipe<FileData> fileProcess = Pipe.flowAsync(() -> processFile(path));
	 *
	 * // Run cleanup in a separate thread after processing is done
	 * Pipe<Void> cleanup = fileProcess.thenAsync(() -> {
	 *     System.out.println("Cleanup running in: " + Thread.currentThread().getName());
	 *     deleteTemporaryFiles();
	 *     updateProcessingStats();
	 * });
	 *
	 * cleanup.await(); // Wait for cleanup to complete
	 * }</pre>
	 *
	 * @param action the action to execute
	 * @return a new pipe that, when awaited, will return null after executing the action
	 */
	Pipe<Void> thenAsync(Runnable action);

	/**
	 * Creates a new pipe that, when this pipe completes normally, is executed with
	 * this pipe's result as the argument to the supplied function. The function must return
	 * another Pipe, which is then "flattened" into the result.
	 * This is useful for chaining asynchronous operations where each depends on the previous one.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * // First fetch a user ID asynchronously
	 * Pipe<Long> userIdPipe = Pipe.flowAsync(() -> getUserId("username"));
	 *
	 * // Then use that ID to fetch user data (also asynchronously)
	 * Pipe<UserData> userDataPipe = userIdPipe.flatMap(id -> {
	 *     // This function returns another Pipe
	 *     return Pipe.flowAsync(() -> fetchUserDetails(id));
	 * });
	 *
	 * // Now userDataPipe represents the final result after both operations
	 * UserData userData = userDataPipe.await();
	 * }</pre>
	 *
	 * @param <U> the type of the returned pipe's result
	 * @param fn  the function to use to compute the returned pipe
	 * @return a new pipe that, when awaited, will return the result of the function
	 */
	<U> Pipe<U> flatMapAsync(Function<? super T, ? extends Pipe<U>> fn);

	/**
	 * Creates a new pipe that, when awaited, will handle exceptions from this pipe
	 * by returning an alternative result calculated from the exception.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * Pipe<UserData> userDataPipe = Pipe.flowAsync(() -> {
	 *     if (networkIsDown) {
	 *         throw new NetworkException("Network unavailable");
	 *     }
	 *     return fetchUserData(userId);
	 * });
	 *
	 * // Provide fallback data in case of exception
	 * Pipe<UserData> safePipe = userDataPipe.recover(ex -> {
	 *     System.err.println("Error: " + ex.getMessage());
	 *     return UserData.getDefaultUserData(); // Fallback value
	 * });
	 *
	 * UserData userData = safePipe.await(); // Never throws exception
	 * }</pre>
	 *
	 * @param fn the function to handle the exception and provide an alternative result
	 * @return a new pipe that will handle exceptions from this pipe
	 */
	Pipe<T> recover(Function<Throwable, ? extends T> fn);

	/**
	 * Creates a new pipe that, when awaited, will return the result of this pipe,
	 * or the given fallback value if this pipe fails with an exception.
	 * This is a simpler version of {@link #recover} when you don't need
	 * to examine the exception.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * Pipe<Double> pricePipe = Pipe.flowAsync(() -> fetchCurrentPrice("ACME"));
	 *
	 * // Use default price if fetch fails
	 * Pipe<Double> safePricePipe = pricePipe.orElse(99.99);
	 *
	 * // This will never throw an exception
	 * double price = safePricePipe.await();
	 * }</pre>
	 *
	 * @param fallback the value to return if this pipe fails
	 * @return a new pipe that will return either this pipe's result or the fallback value
	 */
	Pipe<T> orElse(T fallback);

	/**
	 * Creates a new pipe that, when awaited, will handle exceptions from this pipe
	 * according to the specified strategy.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * Pipe<ReportData> reportPipe = Pipe.flowAsync(() -> generateReport(criteria));
	 *
	 * // Using different strategies:
	 *
	 * // 1. Throw the error (default behavior)
	 * Pipe<ReportData> errorPipe = reportPipe.withRecoveryStrategy(FallbackStrategy.THROW_ERROR, null);
	 *
	 * // 2. Return null on error
	 * Pipe<ReportData> suppressPipe = reportPipe.withRecoveryStrategy(FallbackStrategy.SUPPRESS_ERROR, null);
	 *
	 * // 3. Return a fallback value
	 * ReportData emptyReport = new ReportData();
	 * Pipe<ReportData> fallbackPipe = reportPipe.withRecoveryStrategy(FallbackStrategy.RETURN_FALLBACK, emptyReport);
	 * }</pre>
	 *
	 * @param strategy how to handle exceptions
	 * @param fallback the fallback value to use if strategy is RETURN_FALLBACK
	 * @return a new pipe with the specified exception handling behavior
	 */
	Pipe<T> withRecoveryStrategy(FallbackStrategy strategy, T fallback);

	/**
	 * Creates a new pipe that will execute the given bi-consumer when this pipe completes,
	 * with the result of this pipe and the exception (if any) as arguments.
	 * This is useful for logging or monitoring regardless of success or failure.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * Pipe<OrderStatus> orderPipe = Pipe.flowAsync(() -> processOrder(orderId));
	 *
	 * // Always log the result or exception
	 * Pipe<OrderStatus> loggingPipe = orderPipe.onComplete((result, exception) -> {
	 *     if (exception != null) {
	 *         logger.error("Order processing failed: " + exception.getMessage());
	 *     } else {
	 *         logger.info("Order processed successfully: " + result);
	 *     }
	 * });
	 *
	 * // This will still throw an exception if the original pipe failed
	 * OrderStatus status = loggingPipe.await();
	 * }</pre>
	 *
	 * @param action the action to execute when this pipe completes
	 * @return a new pipe that will complete with the same result as this pipe
	 */
	Pipe<T> onComplete(BiConsumer<? super T, ? super Throwable> action);

	/**
	 * Creates a new pipe that will execute the given bi-function when this pipe completes,
	 * with the result of this pipe and the exception (if any) as arguments.
	 * Unlike {@link #onComplete}, this method allows transforming the result or handling
	 * the exception to produce a new result.
	 *
	 * <p>Example:
	 * <pre>{@code
	 * Pipe<Integer> dataPipe = Pipe.flowAsync(() -> processData());
	 *
	 * // Handle both success and failure cases with transformation
	 * Pipe<ApiResponse> responsePipe = dataPipe.transform((result, ex) -> {
	 *     if (ex != null) {
	 *         return new ApiResponse("error", "Failed: " + ex.getMessage(), null);
	 *     } else {
	 *         return new ApiResponse("success", "Data processed", result);
	 *     }
	 * });
	 *
	 * // This will never throw an exception
	 * ApiResponse response = responsePipe.await();
	 * }</pre>
	 *
	 * @param <U> the type of the returned pipe's result
	 * @param fn  the function to apply to the result and exception
	 * @return a new pipe that will complete with the result of the function
	 */
	<U> Pipe<U> transform(BiFunction<? super T, Throwable, ? extends U> fn);
}
