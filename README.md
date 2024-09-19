# FlyDI

**FlyDI** is a lightweight dependency injection framework for Java. Inspired by the principles of efficiency and minimalism, FlyDI supports constructor and setter injection, providing a simple yet powerful way to manage dependencies in modern applications.

## Features

- **Constructor Injection**: Automatically inject dependencies via constructors.
- **Setter Injection**: Inject dependencies via setter methods.
- **Lightweight**: Minimal overhead, designed to keep things simple and efficient.
- **Flyweight-inspired design**: Encourages efficient use of memory and resources.

## Installation

To use FlyDI in your Java project, simply include the following dependency in your project (Maven/Gradle dependency instructions will go here if available).

## Usage

1. Annotate your classes with `@ManagedBean`.
2. Use `@Inject` to mark constructors or setter methods for dependency injection.

Example:

```java
@ManagedBean
public class MyService {

    private MyRepository repository;

    @Inject
    public MyService(MyRepository repository) {
        this.repository = repository;
    }

    // or using setter injection
    @Inject
    public void setRepository(MyRepository repository) {
        this.repository = repository;
    }
}
