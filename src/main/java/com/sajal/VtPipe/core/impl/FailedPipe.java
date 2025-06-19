package com.sajal.VtPipe.core.impl;

import com.sajal.VtPipe.core.exception.PipeException;

import java.util.Objects;

/**
 * Implementation of a Pipe that is already completed exceptionally with an error.
 *
 * @param <T> the type of the result
 */
public class FailedPipe<T> extends BasePipe<T> {
	private final Throwable exception;

	/**
	 * Creates a new FailedPipe with the given exception.
	 *
	 * @param exception the exception that caused the failure
	 */
	public FailedPipe(Throwable exception) {
		this.exception = Objects.requireNonNull(exception, "Exception cannot be null");
	}

	@Override
	public T await() {
		if (exception instanceof RuntimeException) {
			throw (RuntimeException) exception;
		} else if (exception instanceof Error) {
			throw (Error) exception;
		} else {
			throw new PipeException(exception);
		}
	}

	@Override
	public boolean isCompleted() {
		return true;
	}

	@Override
	public boolean isFailed() {
		return true;
	}

	@Override
	public boolean halt(boolean mayInterruptIfRunning) {
		return false; // Cannot halt an already failed pipe
	}

	@Override
	public boolean isHalted() {
		return false;
	}
}
