# GMD Maven Plugin release history

## v3.0.3, unreleased
- resolve all output types without JavaFX dependencies
- resolve relative source and target paths from the Maven project base directory
- validate output types and source/target directories before processing
- remove JavaFX runtime dependency from the plugin

## v3.0.2, 2026-02-06
- use CI-friendly parent version (`${revision}`) instead of a fixed parent version
- add configurable dependency versions: `groovyVersion`, `log4jVersion`, `gmdVersion`, `ivyVersion`
- resolve runtime dependencies dynamically with Maven Resolver and execute `GmdProcessor` in a forked JVM classpath
- add fallback mode to bundled dependencies when dynamic resolution is unavailable
- upgrade plugin/dependency tooling (`maven.version 3.9.11 -> 3.9.12`, `maven-plugin-annotations 3.15.1 -> 3.15.2`, `maven-plugin-testing-harness 3.3.0 -> 3.5.0`)

## version 1.0.1, in progress
- add more checks to GmdMavenPlugin and improve error messages

## v1.0.0, 2025-04-29
- Initial release
