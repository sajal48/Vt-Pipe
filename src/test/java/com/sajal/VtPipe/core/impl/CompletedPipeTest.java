package com.sajal.VtPipe.core.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompletedPipe Tests")
public class CompletedPipeTest {

	@Test
	@DisplayName("CompletedPipe should be immediately completed with result")
	void completedPipeShouldBeImmediatelyCompletedWithResult() {
		String expectedResult = "test-result";
		CompletedPipe<String> pipe = new CompletedPipe<>(expectedResult);

		assertTrue(pipe.isCompleted());
		assertFalse(pipe.isFailed());
		assertEquals(expectedResult, pipe.await());
	}

	@Test
	@DisplayName("CompletedPipe should work with null result")
	void completedPipeShouldWorkWithNullResult() {
		CompletedPipe<String> pipe = new CompletedPipe<>(null);

		assertTrue(pipe.isCompleted());
		assertFalse(pipe.isFailed());
		assertNull(pipe.await());
	}


	@Test
	@DisplayName("Transformations should execute synchronously")
	void transformationsShouldExecuteSynchronously() {
		AtomicBoolean executed = new AtomicBoolean(false);
		CompletedPipe<Integer> pipe = new CompletedPipe<>(42);
		pipe.mapAsync(i -> {
			executed.set(true);
			return "Value: " + i;
		}).await();

		// The transformation should execute immediately
		assertTrue(executed.get());
	}
}
