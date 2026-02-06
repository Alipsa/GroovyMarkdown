# GMD Gradle Plugin release history

## v3.0.2, 2026-02-06
- use CI-friendly parent version (`${revision}`) instead of a fixed parent version
- upgrade Gradle wrapper [8.13 -> 9.3.1]
- upgrade test baseline to JUnit BOM [5.13.4 -> 6.0.2]
- add configurable `javaFxVersion` parameter
- include platform-specific JavaFX runtime dependencies (`javafx-base`, `javafx-graphics`, `javafx-controls`, `javafx-swing`, `javafx-web`)
- update plugin defaults to current stack (`groovy 5.0.4`, `log4j 2.25.3`, `gmd 3.0.2`)
- upgrade Maven-side `exec-maven-plugin` [3.5.1 -> 3.6.3]
