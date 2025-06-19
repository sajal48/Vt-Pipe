package com.sajal.VtPipe.core.exception;

/**
 * Base exception for all errors occurring within the VtPipe framework.
 * This provides a common exception type for all pipeline-related errors.
 */
public class PipeException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new PipeException with no detail message.
	 */
	public PipeException() {
		super();
	}

	/**
	 * Constructs a new PipeException with the specified detail message.
	 *
	 * @param message the detail message
	 */
	public PipeException(String message) {
		super(message);
	}

	/**
	 * Constructs a new PipeException with the specified detail message and cause.
	 *
	 * @param message the detail message
	 * @param cause   the cause of the exception
	 */
	public PipeException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a new PipeException with the specified cause.
	 *
	 * @param cause the cause of the exception
	 */
	public PipeException(Throwable cause) {
		super(cause);
	}
}
