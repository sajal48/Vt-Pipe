package com.sajal.VtPipe.core;

import com.sajal.VtPipe.core.exception.PipeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pipe Interface Tests")
public class PipeTest {
	@Test
	@DisplayName("mapAsync() should transform the result")
	void mapAsyncShouldTransformResult() {
		Pipe<Integer> pipe = Pipe.completed(5);

		Pipe<String> mappedPipe = pipe.mapAsync(i -> "Value: " + i);

		assertEquals("Value: 5", mappedPipe.await());
	}

	@Test
	@DisplayName("mapAsync() should propagate exceptions")
	void mapAsyncShouldPropagateExceptions() {
		Exception originalException = new RuntimeException("Original");
		Pipe<Integer> pipe = Pipe.failed(originalException);

		Pipe<String> mappedPipe = pipe.mapAsync(i -> "Value: " + i);

		Exception caughtException = assertThrows(RuntimeException.class, mappedPipe::await);
		assertEquals(originalException, caughtException);
	}

	@Test
	@DisplayName("mapAsync() should handle exceptions in the mapper")
	void mapAsyncShouldHandleExceptionsInMapper() {
		Pipe<Integer> pipe = Pipe.completed(5);
		RuntimeException expectedException = new RuntimeException("Mapper exception");

		Pipe<String> mappedPipe = pipe.mapAsync(i -> {
			throw expectedException;
		});

		Exception caughtException = assertThrows(RuntimeException.class, mappedPipe::await);
		assertEquals(expectedException, caughtException);
	}

	@Test
	@DisplayName("thenAsync() should chain operations")
	void thenAsyncShouldChainOperations() {
		List<String> executionOrder = new ArrayList<>();

		Pipe<String> pipe = Pipe.flowAsync(() -> {
			executionOrder.add("first");
			return "first-result";
		});

		Pipe<String> chainedPipe = pipe.flatMapAsync(result -> {
			executionOrder.add("second");
			return Pipe.completed("second-result");
		});

		assertEquals("second-result", chainedPipe.await());
		assertEquals(List.of("first", "second"), executionOrder);
	}

	@Test
	@DisplayName("consumeAsync() should consume the result")
	void consumeAsyncShouldConsumeResult() {
		AtomicReference<String> capturedResult = new AtomicReference<>();

		Pipe<String> pipe = Pipe.completed("test-result");

		Pipe<Void> consumedPipe = pipe.consumeAsync(capturedResult::set);

		consumedPipe.await();
		assertEquals("test-result", capturedResult.get());
	}

	@Test
	@DisplayName("mapAsync() should transform the result")
	void mapAsyncShouldTransformResultAgain() {
		Pipe<Integer> pipe = Pipe.completed(5);

		Pipe<String> transformedPipe = pipe.mapAsync(i -> "Value: " + i);

		assertEquals("Value: 5", transformedPipe.await());
	}

	@Test
	@DisplayName("combination of pipes should combine results")
	void combinationOfPipesShouldCombineResults() {
		Pipe<Integer> pipe1 = Pipe.completed(5);
		Pipe<Integer> pipe2 = Pipe.completed(10);

		// Manually combine the pipes since there's no direct thenCombine method
		Pipe<Integer> combinedPipe = pipe1.flatMapAsync(value1 ->
				pipe2.mapAsync(value2 -> value1 + value2)
		);

		assertEquals(15, combinedPipe.await().intValue());
	}

	@Test
	@DisplayName("flatMapAsync() should chain pipes")
	void flatMapAsyncShouldChainPipes() {
		Pipe<Integer> pipe = Pipe.completed(5);

		Pipe<String> composedPipe = pipe.flatMapAsync(i -> Pipe.completed("Value: " + i));

		assertEquals("Value: 5", composedPipe.await());
	}

	@Test
	@DisplayName("recover() should handle exceptions")
	void recoverShouldHandleExceptions() {
		Exception exception = new RuntimeException("Test exception");
		Pipe<String> failedPipe = Pipe.failed(exception);

		Pipe<String> recoveredPipe = failedPipe.recover(ex -> "Recovered: " + ex.getMessage());

		assertEquals("Recovered: Test exception", recoveredPipe.await());
	}

	@Test
	@DisplayName("recover() should not be called if no exception")
	void recoverShouldNotBeCalledIfNoException() {
		AtomicBoolean recoveryFunctionCalled = new AtomicBoolean(false);

		Pipe<String> successPipe = Pipe.completed("success");

		Pipe<String> recoveredPipe = successPipe.recover(ex -> {
			recoveryFunctionCalled.set(true);
			return "recovered";
		});

		assertEquals("success", recoveredPipe.await());
		assertFalse(recoveryFunctionCalled.get(), "Recovery function should not be called");
	}
	// Note: It seems whenComplete is not available in the interface
	// We're replacing these tests with tests for consumeAsync and recover

	@Test
	@DisplayName("consumeAsync should handle successful results")
	void consumeAsyncShouldHandleSuccessfulResults() {
		AtomicReference<String> resultCapture = new AtomicReference<>();

		Pipe<String> successPipe = Pipe.completed("success");

		successPipe.consumeAsync(result -> {
			resultCapture.set(result);
		}).await();

		assertEquals("success", resultCapture.get());
	}

	@Test
	@DisplayName("recover should handle exceptions properly")
	void recoverShouldHandleExceptionsProperly() {
		AtomicReference<Throwable> exceptionCapture = new AtomicReference<>();

		Exception exception = new RuntimeException("Test exception");
		Pipe<String> failedPipe = Pipe.failed(exception);

		Pipe<String> recoveredPipe = failedPipe.recover(ex -> {
			exceptionCapture.set(ex);
			return "recovered";
		});

		String result = recoveredPipe.await();

		assertEquals("recovered", result);
		assertEquals(exception, exceptionCapture.get());
	}

	@Test
	@DisplayName("orElse() should handle exceptions")
	void orElseShouldHandleExceptions() {
		Exception exception = new RuntimeException("Test exception");
		Pipe<String> failedPipe = Pipe.failed(exception);

		Pipe<String> recoveredPipe = failedPipe.orElse("Recovered value");

		assertEquals("Recovered value", recoveredPipe.await());
	}

	@Test
	@DisplayName("await() should block until completion")
	void awaitShouldBlockUntilCompletion() {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicInteger result = new AtomicInteger(0);

		Pipe<Integer> pipe = Pipe.flowAsync(() -> {
			try {
				latch.await(); // Wait for signal
				return 42;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		});

		// Start a thread to get the result
		Thread awaitThread = new Thread(() -> {
			result.set(pipe.await());
		});
		awaitThread.start();

		// Make sure the thread is waiting
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		assertEquals(0, result.get(), "Result should not be set yet");

		// Allow the pipe to complete
		latch.countDown();

		// Wait for the await thread to finish
		try {
			awaitThread.join(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		assertEquals(42, result.get(), "Result should be set after pipe completes");
	}

	@Test
	@DisplayName("await(timeout) should respect timeout")
	void awaitWithTimeoutShouldRespectTimeout() {
		Pipe<String> pipe = Pipe.flowAsync(() -> {
			sleep(500);
			return "result";
		});

		assertThrows(PipeException.class, () -> pipe.await(Duration.ofMillis(50)));
	}

	@Test
	@DisplayName("Fork and join pattern should work")
	void forkAndJoinPatternShouldWork() {
		// Fork multiple tasks
		Pipe<Integer> task1 = Pipe.flowAsync(() -> {
			sleep(50);
			return 10;
		});

		Pipe<Integer> task2 = Pipe.flowAsync(() -> {
			sleep(30);
			return 20;
		});

		Pipe<Integer> task3 = Pipe.flowAsync(() -> {
			sleep(10);
			return 30;
		});
		// Join results using flatMapAsync
		Pipe<Integer> combined = task1.flatMapAsync(result1 ->
				task2.flatMapAsync(result2 ->
						task3.mapAsync(result3 ->
								result1 + result2 + result3)));

		assertEquals(60, combined.await().intValue());
	}

	void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}
}
