package com.sajal.VtPipe.core.exception;

/**
 * Exception thrown when a pipeline execution is cancelled or interrupted.
 */
public class PipeCancellationException extends PipeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new PipeCancellationException with the specified detail message.
	 *
	 * @param message the detail message
	 */
	public PipeCancellationException(String message) {
		super(message);
	}

	/**
	 * Constructs a new PipeCancellationException with the specified detail message and cause.
	 *
	 * @param message the detail message
	 * @param cause   the cause of the exception
	 */
	public PipeCancellationException(String message, Throwable cause) {
		super(message, cause);
	}
}
