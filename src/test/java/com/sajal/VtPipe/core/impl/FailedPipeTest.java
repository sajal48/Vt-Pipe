package com.sajal.VtPipe.core.impl;

import com.sajal.VtPipe.core.Pipe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FailedPipe Tests")
public class FailedPipeTest {

	@Test
	@DisplayName("FailedPipe should be immediately completed with exception")
	void failedPipeShouldBeImmediatelyCompletedWithException() {
		RuntimeException expectedException = new RuntimeException("test-exception");
		FailedPipe<String> pipe = new FailedPipe<>(expectedException);

		assertTrue(pipe.isCompleted());
		assertTrue(pipe.isFailed());

		Exception caughtException = assertThrows(RuntimeException.class, pipe::await);
		assertEquals(expectedException, caughtException);
	}

	@Test
	@DisplayName("FailedPipe should not accept null exception")
	void failedPipeShouldNotAcceptNullException() {
		assertThrows(NullPointerException.class, () -> new FailedPipe<>(null));
	}

	@Test
	@DisplayName("FailedPipe's cancel should do nothing")
	void cancelShouldDoNothing() {
		RuntimeException expectedException = new RuntimeException("test-exception");
		FailedPipe<String> pipe = new FailedPipe<>(expectedException);

		// No need to call await() as it would throw the exception
		// Instead, check the state directly

		assertTrue(pipe.isCompleted());
		assertTrue(pipe.isFailed());

		// Try canceling - should do nothing
		pipe.halt(true);
		assertFalse(pipe.isHalted());

		Exception caughtException = assertThrows(RuntimeException.class, pipe::await);
		assertEquals(expectedException, caughtException);
	}

	@Test
	@DisplayName("map transformation should not execute on failed pipe")
	void mapShouldNotExecuteOnFailedPipe() {
		AtomicBoolean executed = new AtomicBoolean(false);
		RuntimeException expectedException = new RuntimeException("test-exception");

		FailedPipe<Integer> pipe = new FailedPipe<>(expectedException);
		Pipe<String> mappedPipe = pipe.mapAsync(i -> {
			executed.set(true);
			return "Value: " + i;
		});

		Exception caughtException = assertThrows(RuntimeException.class, mappedPipe::await);
		assertEquals(expectedException, caughtException);
		assertFalse(executed.get(), "Transformation should not execute on failed pipe");
	}

	@Test
	@DisplayName("recover should handle the exception")
	void recoverShouldHandleException() {
		RuntimeException expectedException = new RuntimeException("test-exception");
		FailedPipe<String> pipe = new FailedPipe<>(expectedException);

		Pipe<String> recoveredPipe = pipe.recover(ex -> "Recovered: " + ex.getMessage());

		assertEquals("Recovered: test-exception", recoveredPipe.await());
	}
}
