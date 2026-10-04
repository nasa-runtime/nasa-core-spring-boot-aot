# nasa-core-spring-boot-aot

`nasa-core-spring-boot-aot` generates Spring Boot AOT and GraalVM native-image hints for applications using
`nasa-core`. It discovers the actual application packages at build time and preserves business types used by
`Initialization`, `@ThreadPool`, Java Serialization, and generic reflection utilities. No change to `nasa-core`
and no hard-coded `com` root are required.

[中文](README.md)

## Why it exists

- Carries the dynamic contracts of `nasa-core` into Spring AOT output without per-application metadata files.
- Derives a bounded scan scope from Spring's resolved application structure, regardless of the top-level domain.
- Rejects unknown or unsafe roots at build time instead of producing a native executable that fails later.
- Supplies the shared package discovery and scanner used by the independent Redis and Saga AOT adapters.

## Architecture

```text
Spring BeanDefinition / AutoConfigurationPackages
                    │
                    ▼
        NasaAotApplicationPackages
                    │ bounded packages
                    ▼
           NasaAotClassScanner
                    │
         ┌──────────┼────────────┐
         ▼          ▼            ▼
   reflection   serialization  dynamic members
                                Initialization / @ThreadPool
```

Spring AOT loads the processor from `META-INF/spring/aot.factories`. The processor combines
`AutoConfigurationPackages` with BeanDefinition type metadata for `@SpringBootApplication` and
`@SpringBootConfiguration`. It honors `scanBasePackages` and `scanBasePackageClasses`; when neither is set, the
main application's package is used. No application bean is instantiated during discovery.

The scanner never expands beyond these packages. Public roots such as `com`, `org`, `io`, and `net` are rejected.
Types are sorted by class name before registration to keep generated input deterministic.

## Compatibility

- JDK 21+
- Spring Boot 3.5.x / Spring Framework 6.2.x
- `nasa-core` 1.0.4
- Maven 3.6.3+

The adapter version tracks the target library version. Select an adapter released for the `nasa-core` version in
your application.

## Usage

```xml
<dependency>
    <groupId>io.github.nasa-runtime</groupId>
    <artifactId>nasa-core-spring-boot-aot</artifactId>
    <version>1.0.4</version>
</dependency>
```

Applications located under their main class need no additional configuration:

```bash
./mvnw -Pnative native:compile
```

Add packages outside Boot's application packages through a system property. The Spring Boot Maven Plugin runs
`process-aot` in a separate JVM, so Maven builds must forward the property through `spring-boot.aot.jvmArguments`:

```bash
./mvnw -Dspring-boot.aot.jvmArguments="-Dnasa.aot.scan-packages=com.acme.shared,dev.partner.contracts" \
  -Pnative native:compile
```

When invoking the AOT JVM directly, set `-Dnasa.aot.scan-packages=...`. Each value must be a concrete package with
at least two segments. Wildcards and public top-level roots are not accepted.

## Registered contracts

- Reflection metadata for stable `nasa-core` types and serialization metadata for concrete `Serializable` types.
- Constructors, fields, methods, and nested types for non-synthetic application types in the bounded scope.
- Invocation access to `before`, `initialize`, and `after` on `Initialization` implementations.
- Dynamic field or single-argument method access for `@ThreadPool` members.
- Serialization metadata for concrete application `Serializable` types.

Full member registration follows from the generic reflection API accepting arbitrary business types. Keep the
application root focused, or use marker classes, to limit analysis time and native metadata size.

## Failure and observability

- Missing application scope stops AOT and points to `nasa.aot.scan-packages`.
- Unsafe roots stop AOT; there is no classpath-wide fallback.
- Unreadable resources or incomplete type dependencies stop AOT with the package or class name.
- The processor only generates hints. It starts no thread, opens no connection, and changes no runtime state.

Use the AOT exception and generated files under `target/spring-aot/` as the primary diagnostics.

## Non-goals

This project does not replace Spring Boot's own inference, install GraalVM, cover arbitrary third-party libraries,
or change JVM behavior in `nasa-core`. Applications remain responsible for JNI, runtime bytecode, scripting, and
resources loaded through their own dynamic paths.

## Build and release

```bash
mvn clean verify
```

See [CONTRIBUTING.en.md](CONTRIBUTING.en.md), [SECURITY.en.md](SECURITY.en.md), and
[RELEASE.en.md](RELEASE.en.md). The project is dual-licensed under Apache-2.0 OR MIT.
