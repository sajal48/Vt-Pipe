# VtPipe: Virtual Thread Pipeline Library

![Java Version](https://img.shields.io/badge/Java-17%2B-blue.svg)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

A lightweight, high-performance asynchronous pipeline library for Java, designed specifically to leverage virtual threads introduced in Java 21.

## Table of Contents
- [Introduction](#introduction)
- [Key Features](#key-features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
- [Core Concepts](#core-concepts)
  - [What is a Pipe?](#what-is-a-pipe)
  - [Creating Pipes](#creating-pipes)
  - [Working with Pipe Results](#working-with-pipe-results)
- [Advanced Features](#advanced-features)
  - [Combining Multiple Pipes](#combining-multiple-pipes)
  - [Error Handling](#error-handling)
- [Common Patterns](#common-patterns)
  - [Circuit Breaker](#circuit-breaker)
  - [Federated Search](#federated-search)
  - [First Successful Result](#first-successful-result)
- [Example: Building a Pipeline](#example-building-a-pipeline)
- [Performance Considerations](#performance-considerations)
- [Best Practices](#best-practices)
- [Contributing](#contributing)
- [License](#license)

## Introduction

VtPipe (Virtual Thread Pipeline) is a modern asynchronous programming library that makes it easy to write clean, efficient concurrent code in Java. Built on Java's virtual threads, VtPipe provides a fluent API for composing asynchronous operations without the complexity of traditional concurrent programming models.

## Key Features

* **Virtual Thread Based**: Efficiently handles thousands of concurrent operations without thread pool configuration headaches.
* **Fluent API**: Compose operations with chained method calls for readable, maintainable code.
* **Error Handling**: Comprehensive error management with recovery mechanisms.
* **Transformation**: Map and transform results easily.
* **Composition**: Combine results from multiple asynchronous operations.
* **Performance**: Designed for high throughput and low overhead.
* **Zero Dependencies**: Just pure Java 21+, no external libraries required.

## Getting Started

### Prerequisites

* Java 21 or higher

### Installation

Add VtPipe to your project:

```gradle
implementation 'com.sajal:vtpipe:1.0.0'
```

## Core Concepts

### What is a Pipe?

A `Pipe<T>` represents an asynchronous operation that will eventually produce a result of type `T`. Unlike `CompletableFuture`, Pipes are specifically designed to work with Java's virtual threads, offering a more lightweight approach to concurrency.

A Pipe can be in one of several states:
* **Running**: The operation is still in progress
* **Completed**: The operation has finished successfully with a result
* **Failed**: The operation has completed with an error
* **Halted**: The operation was canceled and will not produce any result

### Creating Pipes

There are several ways to create a Pipe:

```java
// From a supplier function
Pipe<String> pipe1 = Pipe.flowAsync(() -> "Hello, world!");

// From a completed value
Pipe<Integer> pipe2 = Pipe.completed(42);

// From an exception
Pipe<String> pipe3 = Pipe.failed(new RuntimeException("Something went wrong"));

// From a Callable
Pipe<String> pipe4 = Pipe.callAsync(() -> {
    // Potentially long-running operation
    return "Result";
});
```

### Working with Pipe Results

```java
// Wait for completion and get result
String result = pipe.await();

// Transform result (mapping)
Pipe<Integer> lengthPipe = pipe.mapAsync(str -> str.length());

// Chain multiple operations
Pipe<String> chain = Pipe.flowAsync(() -> fetchUser())
    .flatMapAsync(user -> Pipe.flowAsync(() -> fetchUserPosts(user)))
    .mapAsync(posts -> processUserPosts(posts));

// Handle errors
Pipe<String> recovered = pipe.recover(ex -> "Default value");

// Provide fallback value
Pipe<String> withDefault = pipe.orElse("Default value");
```

### Understanding mapAsync vs flatMapAsync

One of the most powerful features in VtPipe is the ability to compose operations using `mapAsync` and `flatMapAsync`. Understanding the difference between these two methods is crucial:

#### mapAsync

```java
<U> Pipe<U> mapAsync(Function<? super T, ? extends U> fn);
```

* **Purpose**: Transforms a value inside a pipe into another value
* **Input**: A function that converts T to U
* **Output**: A new Pipe<U>
* **Use when**: You need to transform data without creating new asynchronous operations

```java
// String -> Integer transformation
Pipe<Integer> lengthPipe = stringPipe.mapAsync(str -> str.length());
```

#### flatMapAsync

```java
<U> Pipe<U> flatMapAsync(Function<? super T, ? extends Pipe<U>> fn);
```

* **Purpose**: Chains asynchronous operations together
* **Input**: A function that converts T to Pipe<U>
* **Output**: A new Pipe<U> (flattens the nested Pipe)
* **Use when**: Your transformation creates another asynchronous operation

```java
// Sequential async operations (one depends on the result of previous)
Pipe<UserProfile> profilePipe = userIdPipe.flatMapAsync(userId -> 
    // This returns another Pipe
    fetchUserDetailsAsync(userId)
);
```

#### Visual Comparison

**mapAsync flow**:
```
Pipe<T> → [Function<T,U>] → Pipe<U>
```

**flatMapAsync flow**:
```
Pipe<T> → [Function<T,Pipe<U>>] → Pipe<Pipe<U>> → (flattened to) → Pipe<U>
```

#### When to Use Which

* Use **mapAsync** for simple transformations (calculate, format, convert)
* Use **flatMapAsync** when:
  * The transformation itself returns a Pipe
  * You need to chain dependent asynchronous operations
  * You want to avoid nested callbacks or pyramid of doom

This distinction is similar to the `map` vs `flatMap` concept in functional programming and stream operations, but specifically designed for asynchronous operations with virtual threads.

## Advanced Features

### Combining Multiple Pipes

```java
// Wait for all pipes to complete
Pipe<List<Result>> combinedResults = Pipe.all(pipe1, pipe2, pipe3);

// Complete when any pipe completes (race)
Pipe<Result> firstResult = Pipe.any(pipe1, pipe2, pipe3);
```

### Error Handling

```java
pipe.mapAsync(this::processData)
   .recover(ex -> {
       if (ex instanceof IOException) {
           return handleIOException((IOException) ex);
       } else {
           return fallbackValue;
       }
   })
   .consumeAsync(result -> displayResult(result));
```

## Common Patterns

### Circuit Breaker

```java
// Try primary source, fall back to backup if primary fails
Pipe<Data> result = primaryDataSource()
    .recover(ex -> backupDataSource());
```

### Federated Search

```java
// Search across multiple sources and combine results
Pipe<List<Result>> federatedSearch = Pipe.all(
    searchSource1(query),
    searchSource2(query),
    searchSource3(query)
).mapAsync(results -> {
    // Combine and sort all results
    return combineResults(results);
});
```

### First Successful Result

```java
// Return result from the first successful source
Pipe<Data> firstSuccessful = Pipe.any(
    sourceThatMightFail1(),
    sourceThatMightFail2(),
    reliableButSlowSource()
);
```

## Example: Building a Pipeline

```java
public class PipeExample {
    public static void main(String[] args) {
        // Simple pipeline example
        Pipe<String> dataPipe = Pipe.flowAsync(() -> {
            // Simulate network call
            Thread.sleep(100);
            return "raw data";
        }).mapAsync(data -> {
            // Transform data
            return data.toUpperCase();
        }).onComplete((result, error) -> {
            // Side effect when complete
            if (error == null) {
                System.out.println("Pipeline completed with: " + result);
            } else {
                System.out.println("Pipeline failed: " + error.getMessage());
            }
        });

        // Wait for result
        String result = dataPipe.await();
        System.out.println("Final result: " + result);

        // Example of error handling
        Pipe<String> failingPipe = Pipe.flowAsync(() -> {
            throw new RuntimeException("Something went wrong!");
        });

        // Handle the error using recover
        Pipe<String> recoveredPipe = failingPipe.recover(ex -> {
            System.out.println("Recovered from: " + ex.getMessage());
            return "Recovery value";
        });
        
        // Get the recovered result
        String recoveredResult = recoveredPipe.await();
        System.out.println("Final result after recovery: " + recoveredResult);
    }
}
```

## Performance Considerations

* VtPipe is designed for I/O-bound operations that can benefit from high concurrency
* Virtual threads are not a solution for CPU-bound tasks; consider using parallel streams instead
* Each pipe operation has minimal overhead, making VtPipe suitable for high-throughput scenarios

## Best Practices

* Use `mapAsync` and `flatMapAsync` to create clean transformation pipelines
* Always handle exceptions either with `recover` or by catching exceptions from `await()`
* For resource cleanup, consider using `onComplete` to perform cleanup actions
* When combining multiple async operations, prefer `Pipe.all()` over nested chains
* For timeout scenarios, use `await(timeout)` instead of complex timer logic

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
