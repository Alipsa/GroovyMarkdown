# GMD Gradle Plugin release history

## v3.1.1, in progress
- replace the deprecated `Project.getProperties()` calls used by signing configuration with `findProperty`, keeping the plugin compatible with Gradle 10
- make `processGmd` compatible with the Gradle configuration cache and parallel execution
- declare configuration-cache support in the Plugin Portal metadata and disable build caching for potentially non-deterministic PDF output

## v3.1.0, 2026-08-02
- resolve all output types without JavaFX dependencies
- declare `processGmd` inputs and outputs and remove stale generated files
- validate output types and source/target directories before processing

## v3.0.2, 2026-02-06
- use CI-friendly parent version (`${revision}`) instead of a fixed parent version
- upgrade Gradle wrapper [8.13 -> 9.3.1]
- upgrade test baseline to JUnit BOM [5.13.4 -> 6.0.2]
- update plugin defaults to current stack (`groovy 5.0.8`, `log4j 2.26.1`, `gmd 3.0.2`)
- upgrade Maven-side `exec-maven-plugin` [3.5.1 -> 3.6.3]
