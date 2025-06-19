package com.sajal.VtPipe.core.exception;

/**
 * Exception thrown when a timeout occurs during pipeline execution.
 */
public class PipeTimeoutException extends PipeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new PipeTimeoutException with the specified detail message.
	 *
	 * @param message the detail message
	 */
	public PipeTimeoutException(String message) {
		super(message);
	}

	/**
	 * Constructs a new PipeTimeoutException with the specified detail message and cause.
	 *
	 * @param message the detail message
	 * @param cause   the cause of the exception
	 */
	public PipeTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}
}
