# Quick Start Example

This file contains quick examples to help you get started with VtPipe.

## Basic Usage

```java
import com.sajal.VtPipe.core.Pipe;

public class QuickStart {
    public static void main(String[] args) {
        // 1. Create a simple pipe
        Pipe<String> simplePipe = Pipe.flowAsync(() -> {
            // Simulate some work
            Thread.sleep(100);
            return "Hello, VtPipe!";
        });

        // 2. Get the result (blocks until complete)
        String result = simplePipe.await();
        System.out.println(result); // Output: Hello, VtPipe!

        // 3. Transform the result
        Pipe<Integer> transformedPipe = Pipe.completed("VtPipe")
            .mapAsync(str -> str.length());
        
        System.out.println(transformedPipe.await()); // Output: 6
    }
}
```

## Error Handling

```java
import com.sajal.VtPipe.core.Pipe;

public class ErrorHandlingExample {
    public static void main(String[] args) {
        // 1. Create a pipe that throws an exception
        Pipe<String> failingPipe = Pipe.flowAsync(() -> {
            if (true) throw new RuntimeException("Something went wrong");
            return "This will never be returned";
        });

        // 2. Handle the exception with recover
        Pipe<String> recoveredPipe = failingPipe.recover(ex -> {
            System.out.println("Caught: " + ex.getMessage());
            return "Fallback value";
        });

        // 3. Get the recovered result
        String result = recoveredPipe.await();
        System.out.println(result); // Output: Fallback value

        // 4. Alternative: Provide a default value with orElse
        String orElseResult = failingPipe.orElse("Default value").await();
        System.out.println(orElseResult); // Output: Default value
    }
}
```

## Understanding mapAsync vs flatMapAsync

```java
import com.sajal.VtPipe.core.Pipe;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MapVsFlatMapExample {
    public static void main(String[] args) {
        // Example data
        Pipe<String> userIdPipe = Pipe.completed("user-123");
        
        // Example 1: Using mapAsync for direct transformation
        Pipe<String> userGreetingPipe = userIdPipe.mapAsync(userId -> {
            // Just a direct transformation, returns String
            return "Hello, user " + userId;
        });
        
        // The result is available immediately with no further awaiting
        String greeting = userGreetingPipe.await();
        System.out.println(greeting); // Output: Hello, user user-123
        
        // Example 2: Using flatMapAsync for chained async operations
        Pipe<UserDetails> userDetailsPipe = userIdPipe.flatMapAsync(userId -> {
            // This returns another Pipe (an async operation)
            return fetchUserDetailsAsync(userId);
        });
        
        // Now we can chain another async operation that depends on the user details
        Pipe<List<Order>> ordersPipe = userDetailsPipe.flatMapAsync(userDetails -> {
            // This also returns another Pipe (another async operation)
            return fetchUserOrdersAsync(userDetails.getId());
        });
        
        // The final result contains orders after both async operations complete
        List<Order> orders = ordersPipe.await();
        System.out.println("Fetched " + orders.size() + " orders");
        
        // Without flatMapAsync, we would need nested awaits:
        // UserDetails details = fetchUserDetailsAsync(userId).await();
        // List<Order> orders = fetchUserOrdersAsync(details.getId()).await();
    }
    
    // Simulated async operations that return Pipes
    private static Pipe<UserDetails> fetchUserDetailsAsync(String userId) {
        return Pipe.flowAsync(() -> {
            // Simulate network delay
            Thread.sleep(100);
            return new UserDetails(userId, "John Doe", "john@example.com");
        });
    }
    
    private static Pipe<List<Order>> fetchUserOrdersAsync(String userId) {
        return Pipe.flowAsync(() -> {
            // Simulate network delay
            Thread.sleep(150);
            return List.of(
                new Order("order-1", 99.99),
                new Order("order-2", 49.95)
            );
        });
    }
    
    // Data classes
    record UserDetails(String id, String name, String email) {}
    record Order(String id, double amount) {}
}
```

## Combining Pipes

```java
import com.sajal.VtPipe.core.Pipe;
import java.util.List;

public class CombinationExample {
    public static void main(String[] args) {
        // 1. Create multiple pipes
        Pipe<String> firstPipe = Pipe.flowAsync(() -> {
            Thread.sleep(100);
            return "First";
        });

        Pipe<String> secondPipe = Pipe.flowAsync(() -> {
            Thread.sleep(50);
            return "Second";
        });

        Pipe<String> thirdPipe = Pipe.flowAsync(() -> {
            Thread.sleep(150);
            return "Third";
        });

        // 2. Wait for all pipes to complete
        Pipe<List<String>> allResults = Pipe.all(firstPipe, secondPipe, thirdPipe);
        List<String> results = allResults.await();
        System.out.println(results); // Output: [First, Second, Third]

        // 3. Complete when any pipe completes (second pipe should be fastest)
        Pipe<String> firstCompleted = Pipe.any(firstPipe, secondPipe, thirdPipe);
        String fastestResult = firstCompleted.await();
        System.out.println(fastestResult); // Output: Second
    }
}
```

## Real-world Example: Parallel API Requests

```java
import com.sajal.VtPipe.core.Pipe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class ParallelApiRequestsExample {
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) {
        // 1. Create pipes for different API endpoints
        Pipe<String> userPipe = fetchJson("https://api.example.com/user/123");
        Pipe<String> postsPipe = fetchJson("https://api.example.com/user/123/posts");
        Pipe<String> statsPipe = fetchJson("https://api.example.com/user/123/stats");

        // 2. Process all data in parallel
        Pipe<UserProfile> profilePipe = Pipe.all(userPipe, postsPipe, statsPipe)
            .mapAsync(results -> {
                String userData = results.get(0);
                String postsData = results.get(1);
                String statsData = results.get(2);
                
                // Process the data and create a user profile
                return createUserProfile(userData, postsData, statsData);
            });

        // 3. Handle both success and failure cases
        UserProfile profile = profilePipe
            .recover(ex -> {
                System.err.println("Failed to load profile: " + ex.getMessage());
                return createDefaultProfile();
            })
            .await();

        System.out.println("Loaded profile for: " + profile.username());
    }

    private static Pipe<String> fetchJson(String url) {
        return Pipe.flowAsync(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .build();
                
            HttpResponse<String> response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());
                
            if (response.statusCode() != 200) {
                throw new RuntimeException("API error: " + response.statusCode());
            }
            
            return response.body();
        });
    }

    private static UserProfile createUserProfile(String userData, String postsData, String statsData) {
        // In a real app, this would parse JSON and create objects
        return new UserProfile("example_user", "Example User", 42);
    }

    private static UserProfile createDefaultProfile() {
        return new UserProfile("unknown", "Unknown User", 0);
    }

    // Simple record to represent a user profile
    record UserProfile(String username, String displayName, int postCount) {}
}
```

These examples demonstrate the core features of VtPipe and how they can be used to simplify asynchronous programming in Java.
