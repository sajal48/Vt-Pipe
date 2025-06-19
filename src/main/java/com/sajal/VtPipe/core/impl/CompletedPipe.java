package com.sajal.VtPipe.core.impl;

import com.sajal.VtPipe.core.exception.PipeException;

/**
 * Implementation of a Pipe that is already completed with a result.
 *
 * @param <T> the type of the result
 */
public class CompletedPipe<T> extends BasePipe<T> {
	private final T result;

	/**
	 * Creates a new CompletedPipe with the given result.
	 *
	 * @param result the result value
	 */
	public CompletedPipe(T result) {
		this.result = result;
	}

	@Override
	public T await() throws PipeException {
		return result;
	}

	@Override
	public boolean isCompleted() {
		return true;
	}

	@Override
	public boolean isFailed() {
		return false;
	}

	@Override
	public boolean halt(boolean mayInterruptIfRunning) {
		return false; // Cannot halt an already completed pipe
	}

	@Override
	public boolean isHalted() {
		return false;
	}
}
