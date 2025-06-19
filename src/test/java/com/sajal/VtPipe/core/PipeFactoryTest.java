package com.sajal.VtPipe.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PipeFactory Tests")
public class PipeFactoryTest {

	@Test
	@DisplayName("completed() should create a completed pipe with result")
	void completedPipeShouldHaveResult() {
		String expected = "test-result";
		Pipe<String> pipe = Pipe.completed(expected);

		assertTrue(pipe.isCompleted());
		assertFalse(pipe.isFailed());
		assertEquals(expected, pipe.await());
	}

	@Test
	@DisplayName("failed() should create a failed pipe with exception")
	void failedPipeShouldHaveException() {
		Exception expectedException = new RuntimeException("test-exception");
		Pipe<String> pipe = Pipe.failed(expectedException);

		assertTrue(pipe.isFailed());
		assertTrue(pipe.isCompleted());

		Exception actualException = assertThrows(RuntimeException.class, pipe::await);
		assertEquals(expectedException, actualException);
	}

	@Test
	@DisplayName("flowAsync() should execute supplier and complete with result")
	void flowAsyncShouldExecuteSupplier() {
		AtomicBoolean executed = new AtomicBoolean(false);
		String expectedResult = "async-result";

		Pipe<String> pipe = Pipe.flowAsync(() -> {
			executed.set(true);
			return expectedResult;
		});

		String result = pipe.await();

		assertTrue(executed.get());
		assertEquals(expectedResult, result);
		assertTrue(pipe.isCompleted());
	}

	@Test
	@DisplayName("callAsync() should execute callable and complete with result")
	void callAsyncShouldExecuteCallable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean(false);
		String expectedResult = "callable-result";

		Pipe<String> pipe = Pipe.callAsync(() -> {
			executed.set(true);
			return expectedResult;
		});

		String result = pipe.await();

		assertTrue(executed.get());
		assertEquals(expectedResult, result);
		assertTrue(pipe.isCompleted());
	}

	@Test
	@DisplayName("execAsync() should execute runnable")
	void execAsyncShouldExecuteRunnable() {
		AtomicBoolean executed = new AtomicBoolean(false);

		Pipe<Void> pipe = Pipe.execAsync(() -> executed.set(true));

		pipe.await();

		assertTrue(executed.get());
		assertTrue(pipe.isCompleted());
	}

	@Test
	@DisplayName("processAsync() should execute consumer with input")
	void processAsyncShouldExecuteConsumer() {
		AtomicReference<String> capturedInput = new AtomicReference<>();
		String expectedInput = "test-input";

		Pipe<Void> pipe = Pipe.processAsync(capturedInput::set, expectedInput);

		pipe.await();

		assertEquals(expectedInput, capturedInput.get());
		assertTrue(pipe.isCompleted());
	}

	@Test
	@DisplayName("all() should complete when all pipes complete")
	void allShouldCompleteWhenAllPipesComplete() {
		AtomicInteger counter = new AtomicInteger(0);

		Pipe<Integer> pipe1 = Pipe.flowAsync(() -> {
			sleep(50);
			counter.incrementAndGet();
			return 1;
		});

		Pipe<Integer> pipe2 = Pipe.flowAsync(() -> {
			sleep(10);
			counter.incrementAndGet();
			return 2;
		});

		Pipe<Void> allPipe = Pipe.all(pipe1, pipe2);

		allPipe.await();

		assertEquals(2, counter.get());
		assertTrue(pipe1.isCompleted());
		assertTrue(pipe2.isCompleted());
		assertTrue(allPipe.isCompleted());
	}

	@Test
	@DisplayName("all() with empty array should complete immediately")
	void allWithEmptyArrayShouldCompleteImmediately() {
		Pipe<Void> allPipe = Pipe.all();

		assertTrue(allPipe.isCompleted());
	}

	@Test
	@DisplayName("any() should complete when first pipe completes")
	void anyShouldCompleteWhenFirstPipeCompletes() {
		AtomicBoolean firstExecuted = new AtomicBoolean(false);
		AtomicBoolean secondExecuted = new AtomicBoolean(false);
		CountDownLatch latch = new CountDownLatch(1);

		Pipe<Integer> pipe1 = Pipe.flowAsync(() -> {
			sleep(100);
			firstExecuted.set(true);
			return 1;
		});

		Pipe<Integer> pipe2 = Pipe.flowAsync(() -> {
			secondExecuted.set(true);
			latch.countDown();
			return 2;
		});

		Pipe<Integer> anyPipe = Pipe.any(pipe1, pipe2);

		Integer result = anyPipe.await();

		assertEquals(2, result);
		assertTrue(anyPipe.isCompleted());
		assertTrue(secondExecuted.get());

		// Wait a bit to make sure the first one completed
		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		assertTrue(firstExecuted.get());
	}

	@Test
	@DisplayName("any() with single pipe should return that pipe")
	void anyWithSinglePipeShouldReturnThatPipe() {
		Pipe<String> pipe = Pipe.completed("single");
		Pipe<String> anyPipe = Pipe.any(pipe);

		assertEquals("single", anyPipe.await());
	}

	@Test
	@DisplayName("any() with empty array should throw exception")
	void anyWithEmptyArrayShouldThrowException() {
		assertThrows(IllegalArgumentException.class, () -> Pipe.any());
	}

	@Test
	@DisplayName("delay() should complete after specified time")
	void delayShouldCompleteAfterSpecifiedTime() {
		long startTime = System.currentTimeMillis();
		long delayMs = 100;

		Pipe<Void> delayPipe = Pipe.delay(delayMs, TimeUnit.MILLISECONDS);

		delayPipe.await();

		long elapsedTime = System.currentTimeMillis() - startTime;
		assertTrue(elapsedTime >= delayMs, "Delay should be at least " + delayMs + " ms but was " + elapsedTime + " ms");
		assertTrue(delayPipe.isCompleted());
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
