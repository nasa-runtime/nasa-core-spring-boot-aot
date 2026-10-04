# Maven Central Release

## Prerequisites

- The POM uses a non-SNAPSHOT version and the target `nasa-core` version matches both READMEs.
- Central namespace, publishing token, and GPG signing key are configured for the Maven `central` server and local
  signing environment.
- Both READMEs, contribution guides, security policies, licenses, source jar, and Javadoc jar are in the archive.

## Local acceptance

```bash
mvn clean verify
mvn package
jar tf target/nasa-core-spring-boot-aot-1.0.4.jar
jar tf target/nasa-core-spring-boot-aot-1.0.4-sources.jar
jar tf target/nasa-core-spring-boot-aot-1.0.4-javadoc.jar
```

Inspect `META-INF/spring/aot.factories`, READMEs, licenses, and implementation classes from the final jar. Verify
coordinates, project URL, SCM, developers, and licenses from the POM, and confirm the dependency tree has no
SNAPSHOT.

## Release order

1. Commit and push the target branch.
2. Wait for all remote CI checks and verify that the tag, branch, and remote commit SHA match.
3. Obtain explicit maintainer authorization for that coordinate and commit.
4. Run `mvn -Pcentral-release clean deploy`, then inspect signatures and Central validation.
5. Publish the validated deployment in Central Portal. Keep `autoPublish` disabled for an explicit review point.
6. Read back the POM, jar, sources, Javadoc, README, and checksums from Maven Central, then resolve the coordinate
   from an empty Maven project.

If an immutable coordinate omits documentation or archive content, stop acceptance and prepare a new patch release;
local changes do not alter the published artifact. No public upload occurs before step 3 authorization.
