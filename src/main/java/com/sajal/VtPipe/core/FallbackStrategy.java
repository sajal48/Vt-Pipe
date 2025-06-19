package com.sajal.VtPipe.core;

/**
 * Defines different strategies for handling exceptions during pipe execution.
 * This enum provides options for how to respond when a pipe operation fails.
 */
public enum FallbackStrategy {
	/**
	 * Re-throws the original exception to the caller.
	 * This is the default behavior if no fallback strategy is specified.
	 */
	THROW_ERROR,

	/**
	 * Suppresses the error (swallows it) and returns null.
	 * This is useful when errors can be ignored and a null result is acceptable.
	 */
	SUPPRESS_ERROR,
	/**
	 * Uses a fallback value instead of the failed operation result.
	 * This allows providing a default or alternative value when an operation fails.
	 */
	RETURN_FALLBACK
}
