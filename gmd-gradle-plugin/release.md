# GMD Gradle Plugin release history

## v3.2.0, unreleased
- **Breaking:** `GmdGradlePlugin.hasRepository(Project, MavenArtifactRepository)` is removed.
  It was public static API on the plugin class; nothing in the repo called it. The plugin now
  checks for an existing Maven Central declaration itself before adding one, and also detects
  when repositories are settings-managed (`PREFER_SETTINGS` or `FAIL_ON_PROJECT_REPOS`) so it
  never adds a project-level repository Gradle would ignore or reject.
- derive the default GMD core version from a generated plugin-version resource populated from the root Maven revision, failing clearly if metadata is unavailable
- derive the development artifact version from the root Maven revision and require an explicit version when publishing
- upgrade the Gradle Versions Plugin from 0.58.0 to 0.61.0
- upgrade the JUnit BOM from 6.1.2 to 6.1.3
- validate the Gradle Plugin Portal publication before uploading the plugin
- upgrade groovy to 5.1.3
- clean stale generated files only from the dedicated default `build/gmd` output directory; custom target directories retain pre-existing and orphaned GMD output when sources are deleted, renamed, or change output type, while Gradle tracks their expected generated files individually for up-to-date checks
- add a required, non-blank `classpathIdentity` `@Input` to `ProcessGmdTask` so a directly registered task's declared runtime selection invalidates up-to-date checks; `GmdGradlePlugin` derives it from its dependency versions, and direct registrants must wire it from their own configuration or the build fails validation

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
