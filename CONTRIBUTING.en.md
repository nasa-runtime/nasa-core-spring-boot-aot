# Contributing

## Environment

- JDK 21+
- Maven 3.6.3+

Run the complete check:

```bash
mvn clean verify
```

## Change policy

- This repository only adapts `nasa-core` to Spring AOT; it does not copy or alter component business logic.
- Every hint must map to an explainable dynamic access path and have focused `RuntimeHintsPredicates` coverage.
- Application scanning must remain bounded, with no fallback to public roots such as `com`, `org`, or `io`.
- Build-time processing must not instantiate application beans, connect to services, or mutate application state.
- Production comments are written in Chinese and explain purpose, parameters, returns, and important boundaries.
- Update both READMEs when behavior, configuration, failure semantics, or compatibility changes.

Before submitting, check formatting, tests, Javadoc, source archives, the final jar contents, and dependency
convergence. Public API changes should include compatibility impact and migration guidance.
