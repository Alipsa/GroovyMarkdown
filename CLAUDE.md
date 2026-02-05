# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Groovy Markdown (GMD) is a multi-module Maven project that processes Markdown files with embedded Groovy code blocks, converting them to standard Markdown, HTML, or PDF. The project targets Java 21+ and Groovy 5.0.4.

## Build Commands

### Full Build
```bash
mvn clean install                          # Build all modules
./buildAll.sh                              # Build script (sets JDK 21 if available)
```

### Module-Specific Builds
```bash
mvn -pl gmd-core clean package            # Build core library only
mvn -pl gmd-maven-plugin clean package    # Build Maven plugin only
cd gmd-gradle-plugin && ./gradlew build   # Build Gradle plugin only
```

### Testing
```bash
mvn test                                   # Run all tests
mvn -pl gmd-core test                     # Run tests for specific module
```

### Release
```bash
./release.sh                               # Deploy to Maven Central (requires -Prelease profile)
# After release, visit: https://central.sonatype.com/publishing/deployments
```

### Dependency Management
```bash
./checkDependencies.sh                     # Check for dependency updates (Maven + Gradle)
mvn versions:display-plugin-updates        # Maven plugin updates only
mvn versions:display-dependency-updates    # Maven dependency updates only
```

### Running the GUI Test Application
```bash
./runGui.sh                                # Launch GmdTestGui
```

### Command Line Usage
```bash
# Build the fat JAR first
mvn -pl gmd-core clean package

# Use the fat JAR
java -jar gmd-core/target/gmd-3.0.2-SNAPSHOT.jar toHtml input.gmd output.html
java -jar gmd-core/target/gmd-3.0.2-SNAPSHOT.jar toPdf input.gmd output.pdf
java -jar gmd-core/target/gmd-3.0.2-SNAPSHOT.jar toPdfRaw input.gmd output.pdf
```

## Version Management

**IMPORTANT**: The project uses CI-friendly versioning. Change the version in **ONE place only**:

```xml
<!-- In /pom.xml -->
<properties>
  <revision>3.0.2-SNAPSHOT</revision>
</properties>
```

All child modules automatically inherit this version via `${revision}`. The `flatten-maven-plugin` resolves this during build/deployment.

## Project Structure

### Module Organization
```
gmd-parent (root pom)
├── gmd-core             - Core processing library (Groovy)
├── gmd-maven-plugin     - Maven plugin wrapper (Java)
├── gmd-gradle-plugin    - Gradle plugin (Groovy + Maven driver)
└── GmdTestGui          - JavaFX GUI test application (Groovy)
```

### Key Components

#### gmd-core
The core processing engine. Main classes:

- **Gmd** (`Gmd.groovy`): Main API orchestrator
  - `gmdToHtml(text, params)` - Convert GMD to HTML
  - `gmdToMd(text, params)` - Process GMD to Markdown
  - `gmdToPdf(text, params, file)` - Generate PDF from GMD
  - `processHtmlAndSaveAsPdf(html, file)` - Styled PDF with JavaFX WebView
  - `mdToHtml(markdown)` - Standard Markdown to HTML

- **GmdTemplateEngine** (`GmdTemplateEngine.groovy`): Groovy code block processor
  - Parses ```{groovy} blocks
  - Executes Groovy code via GroovyScriptEngine
  - Expands `= expression ` inline variables
  - Supports `echo=false` to hide source code

- **GmdProcessor** (`GmdProcessor.groovy`): Batch file processor
  - `process(sourceDir, targetDir, outputType)` - Process all .gmd files
  - Used by Maven and Gradle plugins

- **HtmlDecorator** (`HtmlDecorator.groovy`): HTML document decoration
  - Adds Bootstrap CSS, HighlightJS, Unicode fonts
  - MathML support for mathematical expressions

- **Printer** (`Printer.groovy`): Custom PrintWriter
  - Handles Matrix tables and charts directly
  - Captures output from Groovy code blocks

#### gmd-maven-plugin
Maven plugin implementation (`GmdMavenPlugin.java`):
- **Mojo**: `processGmd` (default phase: PROCESS_RESOURCES)
- **Smart execution**: Tries dynamic dependency resolution via Maven Resolver, falls back to bundled deps
- **Configurable versions**: groovyVersion, log4jVersion, gmdVersion, ivyVersion, javaFxVersion
- **Platform-aware**: Resolves OS-specific JavaFX classifiers (mac, mac-aarch64, linux, win)

#### gmd-gradle-plugin
Gradle plugin with Maven wrapper:
- `GmdGradlePlugin.groovy`: Implements `Plugin<Project>`
- `GmdGradlePluginParams.groovy`: Configuration interface
- `pom.xml`: Maven driver that delegates to `./gradlew` via exec-maven-plugin

## Processing Pipeline

```
GMD Text Input (markdown with ```{groovy} blocks)
    ↓
GmdTemplateEngine.processCodeBlocks()
    ├─ Execute Groovy code with GroovyScriptEngine
    ├─ Expand `= expression ` inline variables
    └─ Capture output via Printer
    ↓
Standard Markdown
    ↓
Commonmark Parser + GFM Tables Extension
    ↓
HTML
    ├─ Optional: HtmlDecorator adds CSS/JS/fonts
    └─ Optional: JavaFX WebView for JavaScript execution
    ↓
OpenHtmlToPdf (with MathML/SVG support)
    ↓
PDF Output
```

## Key Dependencies

- **Groovy**: 5.0.4 (groovy, groovy-templates, groovy-jsr223)
- **Markdown**: commonmark 0.27.1 + GFM tables extension
- **PDF**: openhtmltopdf 1.1.37 (core, pdfbox, mathml, svg)
- **JavaFX**: 21.0.5 (compile-only) / 23.0.2 (runtime via @Grab)
- **Matrix**: se.alipsa.matrix BOM 2.4.0 (charts, core, xchart)
- **Bootstrap**: 5.3.8 (webjar)
- **Logging**: log4j 2.25.3

## Important Constraints

### Java Version
- **Required**: Java 21 (enforced by maven-enforcer-plugin)
- **Maven**: 3.9.9+ required
- **Module System**: NOT used (useModulePath=false in surefire config)

### JavaFX Versioning
The project locks to JavaFX 23.x for JDK 21 compatibility. The `version-plugin-rules.xml` blocks JavaFX 24+ suggestions.

### Dynamic Dependency Loading
gmd-core uses Groovy `@Grab` to dynamically download JavaFX at runtime, avoiding OS-specific fat JARs. This happens in the `processHtmlAndSaveAsPdf` method.

## Testing

Tests use JUnit 5 (Jupiter 6.0.2):
- `gmd-core/src/test/groovy/test/alipsa/groovy/gmd/`
  - GmdTest.groovy
  - GmdTemplateEngineTest.groovy
  - GmdHighlightTest.groovy
- `gmd-maven-plugin/src/test/java/test/alipsa/gmd/maven/`
- `gmd-gradle-plugin/src/test/groovy/test/alipsa/gmd/gradle/`

## Deployment

### Maven Central (gmd-core, gmd-maven-plugin)
```bash
mvn -Prelease deploy
```
Requires:
- GPG signing configured
- Sonatype credentials in ~/.m2/settings.xml
- Manual publishing at https://central.sonatype.com/publishing/deployments

### Gradle Plugin Portal (gmd-gradle-plugin)
```bash
cd gmd-gradle-plugin
./gradlew publishPlugins
```

### GitHub Releases
The fat JAR (`gmd-core/target/gmd-{version}.jar`) should be attached to GitHub releases for CLI usage.

## Common Development Patterns

### Adding New Gmd Methods
1. Add method to `Gmd.groovy` in gmd-core
2. Update tests in `GmdTest.groovy`
3. If batch processing is needed, update `GmdProcessor.groovy`
4. Update README.md with usage examples

### Modifying Template Processing
1. Edit `GmdTemplateEngine.groovy`
2. Update `GmdTemplateEngineTest.groovy`
3. Consider impact on inline variable expansion (`= expression `)

### Updating Plugin Configurations
Both Maven and Gradle plugins have parallel configuration parameters. When changing defaults:
1. Update `GmdMavenPlugin.java` parameter defaults
2. Update `GmdGradlePluginParams.groovy` property defaults
3. Update respective README files in plugin directories
4. Consider updating parent pom.xml properties if it affects core dependencies

## Special Features

### Inline Variables
Use `= expression ` syntax to embed dynamic values:
```markdown
The result is `= 2 + 2 `
```

### Echo Control
Hide Groovy source code from output:
````markdown
```{groovy echo=false}
def secret = "computed value"
```
````

### Matrix Integration
Matrix tables and charts work directly with the `out` PrintWriter:
```groovy
out.println(matrixTable)
out.println(barChart)
```

### MathML Support
Use HTML entities for math symbols or generate full MathML:
```markdown
X = &sum;(&radic;2&pi; + &#8731;3)
```

## Architecture Notes

### Platform-Specific JavaFX Resolution
The Maven plugin uses Maven Resolver to dynamically determine the OS platform and resolve the correct JavaFX classifier:
- macOS Intel: `mac`
- macOS ARM: `mac-aarch64`
- Linux: `linux`
- Windows: `win`

### Hybrid Gradle-Maven Build
The gmd-gradle-plugin has dual build support:
- **Native Gradle**: `build.gradle` for actual plugin implementation
- **Maven Wrapper**: `pom.xml` delegates Maven commands to `./gradlew`
- This allows the Gradle plugin to participate in the Maven multi-module build

### GroovyDoc as JavaDoc
gmd-core uses maven-antrun-plugin to generate GroovyDoc (since gmavenplus-plugin doesn't support Groovy 5), then packages it as a javadoc.jar classifier for Maven Central compliance.

## Resource Files

- `/gmd-core/src/main/resources/highlightJs/` - HighlightJS library for code syntax highlighting
- `/gmd-core/src/main/resources/fonts/` - Unicode font files for PDF rendering
- `/gmd-core/src/main/assembly/fatjar.xml` - Assembly descriptor for fat JAR
