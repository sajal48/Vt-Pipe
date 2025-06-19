package com.sajal.VtPipe;

import com.sajal.VtPipe.core.Pipe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Advanced Integration Tests for VtPipe")
public class AdvancedIntegrationTest {

	@Test
	@DisplayName("Federated search should return results from all successful sources")
	void federatedSearchShouldReturnResultsFromAllSuccessfulSources() {
		// Setup test data
		List<DataSource> sources = List.of(
				new DataSource("FastDB", 50, true),
				new DataSource("SlowDB", 200, true),
				new DataSource("UnreliableDB", 100, false),
				new DataSource("BackupDB", 150, true)
		);

		FederatedSearchService service = new FederatedSearchService(sources);
		String query = "test-query";

		// Execute search
		Pipe<List<String>> resultsPipe = service.searchAll(query);
		List<String> results = resultsPipe.await();

		// There may be 3 or 4 results depending on if UnreliableDB failed
		assertTrue(results.size() >= 3, "Should have at least 3 results");
		assertTrue(results.size() <= 4, "Should have at most 4 results");

		// Verify expected results are present
		boolean hasFastDB = false;
		boolean hasSlowDB = false;
		boolean hasBackupDB = false;

		for (String result : results) {
			if (result.contains("FastDB")) hasFastDB = true;
			if (result.contains("SlowDB")) hasSlowDB = true;
			if (result.contains("BackupDB")) hasBackupDB = true;
			assertTrue(result.contains(query), "Result should contain the query");
		}

		assertTrue(hasFastDB, "FastDB result should be present");
		assertTrue(hasSlowDB, "SlowDB result should be present");
		assertTrue(hasBackupDB, "BackupDB result should be present");
	}

	@Test
	@DisplayName("First search should return fastest successful result")
	void firstSearchShouldReturnFastestSuccessfulResult() {
		// Setup test data with predictable outcomes
		List<DataSource> sources = List.of(
				new DataSource("SlowDB", 200, true),
				new DataSource("FastDB", 50, true)  // This should be the winner
		);

		FederatedSearchService service = new FederatedSearchService(sources);
		String query = "test-query";

		// Execute search
		Pipe<String> resultPipe = service.searchFirst(query);
		String result = resultPipe.await();

		// The fastest reliable source should win
		assertTrue(result.contains("FastDB"), "FastDB should be the first to respond");
		assertTrue(result.contains(query), "Result should contain the query");
	}

	@Test
	@DisplayName("Circuit breaker pattern with fallback sources")
	void circuitBreakerPatternWithFallbackSources() {
		// Primary data source that will fail
		DataSource primary = new DataSource("PrimaryDB", 50, false) {
			@Override
			Pipe<String> fetchData(String query) {
				return Pipe.flowAsync(() -> {
					// Always fail
					throw new RuntimeException("Primary DB unavailable");
				});
			}
		};

		// Backup source that will succeed
		DataSource backup = new DataSource("BackupDB", 100, true);

		// Try primary first, fall back to backup on failure
		Pipe<String> resultPipe = primary.fetchData("critical-query")
				.recover(ex -> {
					System.out.println("Primary failed, trying backup: " + ex.getMessage());
					return backup.fetchData("critical-query").await();
				});

		// Check result
		String result = resultPipe.await();
		assertTrue(result.contains("BackupDB"), "Should fall back to backup source");
		assertTrue(result.contains("critical-query"), "Result should contain the query");
	}

	@Test
	@DisplayName("Publish-subscribe pattern")
	void publishSubscribePattern() {
		// Create a simple pub-sub system using Pipe

		// Event bus
		class EventBus {
			private final Map<String, List<Pipe<Void>>> subscribers = new ConcurrentHashMap<>();

			void subscribe(String topic, Pipe<Void> handler) {
				subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(handler);
			}

			void publish(String topic, String message) {
				List<Pipe<Void>> handlers = subscribers.getOrDefault(topic, List.of());
				for (Pipe<Void> handler : handlers) {
					// Process in background
					Pipe.flowAsync(() -> {
						try {
							handler.await();
						} catch (Exception e) {
							System.err.println("Handler failed: " + e.getMessage());
						}
						return null;
					});
				}
			}
		}

		// Setup
		EventBus eventBus = new EventBus();
		List<String> receivedMessages = new ArrayList<>();
		CountDownLatch messageLatch = new CountDownLatch(3);

		// Subscribe to topics
		eventBus.subscribe("notifications", Pipe.execAsync(() -> {
			receivedMessages.add("notifications received");
			messageLatch.countDown();
		}));

		eventBus.subscribe("data.updates", Pipe.execAsync(() -> {
			receivedMessages.add("data update received");
			messageLatch.countDown();
		}));

		eventBus.subscribe("logs", Pipe.execAsync(() -> {
			receivedMessages.add("log received");
			messageLatch.countDown();
		}));

		// Publish events
		eventBus.publish("notifications", "New notification");
		eventBus.publish("data.updates", "Data updated");
		eventBus.publish("logs", "System log");

		// Wait for processing
		try {
			messageLatch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			fail("Test interrupted");
		}

		// Verify
		assertEquals(3, receivedMessages.size(), "Should receive all messages");
		assertTrue(receivedMessages.contains("notifications received"));
		assertTrue(receivedMessages.contains("data update received"));
		assertTrue(receivedMessages.contains("log received"));
	}

	// Sample data model
	static class DataSource {
		private final String name;
		private final int latency;
		private final boolean reliable;

		DataSource(String name, int latency, boolean reliable) {
			this.name = name;
			this.latency = latency;
			this.reliable = reliable;
		}

		Pipe<String> fetchData(String query) {
			return Pipe.flowAsync(() -> {
				try {
					// Simulate network latency
					Thread.sleep(latency);

					// Simulate occasional failures for unreliable sources
					if (!reliable && Math.random() < 0.3) {
						throw new RuntimeException("Failed to fetch data from " + name);
					}

					return name + " result for: " + query;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				}
			});
		}
	}

	// Service class for testing
	static class FederatedSearchService {
		private final List<DataSource> dataSources;

		FederatedSearchService(List<DataSource> dataSources) {
			this.dataSources = dataSources;
		}

		Pipe<List<String>> searchAll(String query) {
			List<Pipe<String>> searchPipes = new ArrayList<>();

			// Create pipes for all data sources
			for (DataSource source : dataSources) {
				searchPipes.add(source.fetchData(query));
			}

			// Wait for all results
			return Pipe.flowAsync(() -> {
				List<String> results = new ArrayList<>();

				// Collect all successful results, ignore failures
				for (Pipe<String> pipe : searchPipes) {
					try {
						String result = pipe.await();
						results.add(result);
					} catch (Exception e) {
						// Log and ignore failures
						System.out.println("Search failed: " + e.getMessage());
					}
				}

				return results;
			});
		}

		Pipe<String> searchFirst(String query) {
			List<Pipe<String>> searchPipes = new ArrayList<>();

			// Create pipes for all data sources
			for (DataSource source : dataSources) {
				searchPipes.add(source.fetchData(query));
			}

			// Return first successful result
			return Pipe.any(searchPipes.toArray(new Pipe[0]));
		}
	}
}
