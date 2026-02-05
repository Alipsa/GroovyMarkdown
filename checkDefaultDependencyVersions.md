# checkDefaultDependencyVersions

This script checks if the default dependency versions in both the Gradle and Maven plugins are up to date.

## Usage

```bash
./checkDefaultDependencyVersions
```

The script is automatically called at the end of `./checkDependencies.sh`.

## What it checks

The script extracts and verifies the default versions for:
- **Groovy** (`org.apache.groovy:groovy`)
- **Log4j** (`org.apache.logging.log4j:log4j-core`)
- **GMD Core** (`se.alipsa.gmd:gmd-core`)
- **Ivy** (`org.apache.ivy:ivy`)
- **JavaFX** (`org.openjfx:javafx-web`)

For each dependency, it:
1. Extracts the default version from both plugin source files
2. Checks Maven Central for the latest stable version (filters out alpha, beta, RC, EA releases)
3. Reports if an update is available

## Special cases

### JavaFX
JavaFX 24+ requires Java 23+. Since this project targets Java 21, the script treats JavaFX 23.x as the latest compatible version and doesn't report newer versions as updates.

### GMD Core
GMD Core is part of this project, so its version is checked differently. The script:
1. Reads the version from the parent `pom.xml`
2. Removes `-SNAPSHOT` suffix if present
3. Compares plugin defaults against this project version (not Maven Central)

This ensures that plugins use the current project version for GMD Core.

## Exit codes

- `0`: All dependencies are up to date
- `1`: One or more dependencies have newer versions available

## Files checked

- **Gradle plugin**: `gmd-gradle-plugin/src/main/groovy/se/alipsa/gmd/gradle/GmdGradlePlugin.groovy`
- **Maven plugin**: `gmd-maven-plugin/src/main/java/se/alipsa/gmd/maven/GmdMavenPlugin.java`

## Dependencies

This script requires:
- `bash`
- `./checkVersion` (Groovy script that queries Maven Central)

## Example output

```
===========================================
  Checking Default Dependency Versions
===========================================

Checking Groovy...
  Gradle plugin default: 5.0.4
  Maven plugin default:  5.0.4
  ✓ Gradle: org.apache.groovy:groovy:5.0.4 is the latest stable version

Checking Log4j...
  Gradle plugin default: 2.25.3
  Maven plugin default:  2.25.3
  ✓ Gradle: org.apache.logging.log4j:log4j-core:2.25.3 is the latest stable version

...

===========================================
All default dependency versions are up to date!
===========================================
```
