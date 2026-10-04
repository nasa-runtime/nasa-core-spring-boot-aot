# Security Policy

## Supported releases

The current release line receives security updates. Older lines are not guaranteed patches; upgrade to the latest
available release first. Security advisories for `nasa-core`, Spring Boot, and GraalVM remain owned by those projects.

## Private reporting

Use the repository's **Security → Report a vulnerability** entry on GitHub. Do not disclose uncoordinated details
in a public issue, discussion, or pull request.

Include affected versions, a reproducible scenario, impact, required configuration, and possible mitigations.
Maintainers will acknowledge receipt and coordinate assessment, release, and disclosure timing. Do not attach
credentials, production data, or third-party personal information.

## Boundary

An overly broad application scope can increase analysis time and native metadata, although public top-level roots
are rejected. Application-provided hints, the GraalVM toolchain, upstream dependencies, and runtime deployment
configuration remain outside this repository's direct security boundary.
