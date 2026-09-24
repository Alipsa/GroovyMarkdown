package se.alipsa.gmd.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

/**
 * Maven plugin to process GMD files.
 */
@Mojo(name = "processGmd", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class GmdMavenPlugin extends AbstractMojo {

  @Parameter(name="sourceDir", property = "processGmd.sourceDir", defaultValue = "src/main/gmd")
  private String sourceDir;
  @Parameter(name = "targetDir", property = "processGmd.targetDir", defaultValue = "target/gmd" )
  private String targetDir;
  @Parameter(name = "outputType", property = "processGmd.outputType", defaultValue = "md" )
  private String outputType;

  @Parameter(name = "groovyVersion", property = "processGmd.groovyVersion", defaultValue = "5.1.3")
  private String groovyVersion;

  @Parameter(name = "log4jVersion", property = "processGmd.log4jVersion", defaultValue = "2.26.1")
  private String log4jVersion;

  @Parameter(name = "gmdVersion", property = "processGmd.gmdVersion", defaultValue = "${plugin.version}")
  private String gmdVersion;

  @Parameter(name = "ivyVersion", property = "processGmd.ivyVersion", defaultValue = "2.6.0")
  private String ivyVersion;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Inject
  private RepositorySystem repositorySystem;

  /**
   * Bounded wait after {@link Process#destroyForcibly()} so cancelling the build
   * cannot hang forever on a forked JVM that survives the forced kill.
   */
  private static final int FORCED_TERMINATION_TIMEOUT_SECONDS = 30;

  /**
   * Default constructor.
   */
  public GmdMavenPlugin() {
    super();
  }

  /**
   * The directory where the GMD files are located. Default is src/main/gmd.
   *
   * @return The directory where the GMD files are located.
   */
  public String getSourceDir() {
    return sourceDir;
  }

  /**
   * The directory where the generated files will be written. Default is target/gmd
   *
   * @return The directory where the generated files will be written.
   */
  public String getTargetDir() {
    return targetDir;
  }

  /**
   * The type of output to generate. Can be one of:
   * - md
   * - html
   * - pdf
   *
   * @return The type of output to generate.
   */
  public String getOutputType() {
    return outputType;
  }

  /**
   * The version of Groovy to use. Default is 5.1.3
   *
   * @return The version of Groovy to use.
   */
  public String getGroovyVersion() {
    return groovyVersion;
  }

  /**
   * The version of Log4j to use. Default is 2.26.1
   *
   * @return The version of Log4j to use.
   */
  public String getLog4jVersion() {
    return log4jVersion;
  }

  /**
   * The version of GMD core to use. Defaults to the plugin version.
   *
   * @return The version of GMD core to use.
   */
  public String getGmdVersion() {
    return gmdVersion;
  }

  /**
   * The version of Ivy to use. Default is 2.6.0
   *
   * @return The version of Ivy to use.
   */
  public String getIvyVersion() {
    return ivyVersion;
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      String normalizedOutputType = normalizeOutputType(outputType);
      File srcDir = resolveProjectPath(sourceDir);
      File outputDirectory = resolveProjectPath(targetDir);
      if (!srcDir.exists()) {
        getLog().warn("Source directory " + srcDir.getCanonicalPath() + " does not exist, nothing to do");
        return;
      }
      if (srcDir.isFile()) {
        throw new MojoFailureException(srcDir + " is a file, not a directory");
      }
      if (outputDirectory.exists() && outputDirectory.isFile()) {
        throw new MojoFailureException(outputDirectory + " is a file, not a directory");
      }
      String[] gmdFiles = srcDir.list((directory, name) -> name.endsWith(".gmd"));
      if (gmdFiles == null || gmdFiles.length == 0) {
        getLog().warn("No gmd files found in " + srcDir + ", nothing to do");
        return;
      }

      // Check if we can resolve dependencies dynamically
      boolean canResolveDependencies = repositorySystem != null
          && session != null
          && session.getRepositorySession() != null
          && session.getRepositorySession().getLocalRepositoryManager() != null;

      if (canResolveDependencies) {
        // Resolve dependencies with specified versions
        List<File> classpathFiles = resolveDependencies();

        // Build classpath string
        StringBuilder classpath = new StringBuilder();
        for (File file : classpathFiles) {
          if (classpath.length() > 0) {
            classpath.append(File.pathSeparator);
          }
          classpath.append(file.getAbsolutePath());
        }

        // Execute GmdProcessor in a forked process with custom classpath. The
        // classpath and args are passed via a @-argfile rather than directly on the
        // command line, since a long transitive classpath can approach the ~32 KB
        // command-line length limit on Windows.
        File argFile = createArgFile(classpath.toString(), srcDir.getCanonicalPath(),
            outputDirectory.getCanonicalPath(), normalizedOutputType);
        boolean interrupted = false;
        try {
          List<String> command = new ArrayList<>();
          command.add(getJavaExecutable());
          command.add("@" + argFile.getAbsolutePath());

          ProcessBuilder processBuilder = new ProcessBuilder(command);
          processBuilder.inheritIO();
          Process process = processBuilder.start();
          int exitCode;
          try {
            exitCode = process.waitFor();
          } catch (InterruptedException e) {
            process.destroy();
            try {
              if (!process.waitFor(5, TimeUnit.SECONDS)) {
                warnOnIncompleteForcedTermination(process);
              }
            } catch (InterruptedException swallowed) {
              warnOnIncompleteForcedTermination(process);
            }
            interrupted = true;
            throw new MojoExecutionException("Interrupted while waiting for the GMD processor", e);
          }

          if (exitCode != 0) {
            throw new MojoFailureException("GmdProcessor exited with code " + exitCode);
          }
        } finally {
          // Never let a cleanup failure here replace a pending exception from
          // the try block (e.g. the non-zero exit code above) with a less
          // informative one.
          try {
            Files.deleteIfExists(argFile.toPath());
          } catch (IOException e) {
            getLog().warn("Could not delete temporary classpath argfile " + argFile.getAbsolutePath()
                + ": " + e.getMessage());
          }
          if (interrupted) {
            Thread.currentThread().interrupt();
          }
        }
      } else {
        // Fall back to using GmdProcessor directly with bundled dependencies
        getLog().warn("Cannot resolve custom dependencies, using bundled versions");
        se.alipsa.gmd.core.GmdProcessor gmdProcessor = new se.alipsa.gmd.core.GmdProcessor();
        gmdProcessor.process(srcDir.getCanonicalPath(), outputDirectory.getCanonicalPath(), normalizedOutputType);
      }

      File td = outputDirectory;
      if (td.exists()) {
        getLog().info("Gmd files processed and written to " + td.getCanonicalPath());
      } else {
        getLog().warn(td.getCanonicalPath() + " should exists but does not, something is probably wrong");
      }
    } catch (MojoExecutionException | MojoFailureException e) {
      throw e;
    } catch (DependencyResolutionException e) {
      throw new MojoExecutionException("Failed to resolve dependencies", e);
    } catch (Exception e) {
      String message = "Failed to process gmd files in " + sourceDir;
      if (e.getMessage() != null && !e.getMessage().isBlank()) {
        message += ": " + e.getMessage();
      }
      throw new MojoFailureException(message, e);
    }
  }

  /**
   * {@link Process#destroyForcibly()} is asynchronous, so a caller that returns right
   * after calling it can race the forked JVM's actual exit. Wait for a bounded
   * period so cancellation remains able to return if the forked JVM survives the
   * forced termination. A second interrupt is restored before returning.
   */
  private static ForcedTerminationAwait destroyForciblyAndAwaitTermination(Process process) {
    process.destroyForcibly();
    try {
      return process.waitFor(FORCED_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          ? ForcedTerminationAwait.TERMINATED : ForcedTerminationAwait.TIMED_OUT;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ForcedTerminationAwait.INTERRUPTED;
    }
  }

  /**
   * Both callers of {@link #destroyForciblyAndAwaitTermination(Process)} report the
   * outcome, but a timeout and a second interrupt need different diagnostics: the
   * process may have exited microseconds after the interrupt, so claiming it survived
   * the forced kill would be misleading.
   */
  private void warnOnIncompleteForcedTermination(Process process) {
    switch (destroyForciblyAndAwaitTermination(process)) {
      case TIMED_OUT ->
          getLog().warn("GMD processor did not terminate within "
              + FORCED_TERMINATION_TIMEOUT_SECONDS + " seconds after forced termination");
      case INTERRUPTED ->
          getLog().warn("Interrupted again while awaiting the GMD processor's termination "
              + "after forced termination; its exit status is unknown");
      case TERMINATED -> {
        // Nothing to report.
      }
    }
  }

  /** Outcome of awaiting a forcibly destroyed process, so callers can report it accurately. */
  private enum ForcedTerminationAwait {
    TERMINATED,
    TIMED_OUT,
    INTERRUPTED
  }

  private List<File> resolveDependencies() throws DependencyResolutionException {
    RepositorySystemSession repoSession = session.getRepositorySession();

    List<Dependency> dependencies = new ArrayList<>();
    dependencies.add(new Dependency(new DefaultArtifact("org.apache.groovy:groovy:" + groovyVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.apache.groovy:groovy-templates:" + groovyVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.apache.groovy:groovy-jsr223:" + groovyVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.apache.ivy:ivy:" + ivyVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.apache.logging.log4j:log4j-core:" + log4jVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("se.alipsa.gmd:gmd-core:" + gmdVersion), "runtime"));

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setDependencies(dependencies);
    collectRequest.setRepositories(project.getRemoteProjectRepositories());

    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
    DependencyResult dependencyResult = repositorySystem.resolveDependencies(repoSession, dependencyRequest);

    List<File> classpathFiles = new ArrayList<>();
    for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
      Artifact artifact = artifactResult.getArtifact();
      classpathFiles.add(artifact.getFile());
    }

    return classpathFiles;
  }

  private String normalizeOutputType(String value) throws MojoFailureException {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (!List.of("md", "html", "pdf").contains(normalized)) {
      throw new MojoFailureException("Unknown output type " + value + ", expected either md, html or pdf");
    }
    return normalized;
  }

  private File resolveProjectPath(String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Path cannot be null or blank");
    }
    File candidate = new File(path);
    return candidate.isAbsolute() ? candidate : new File(project.getBasedir(), path);
  }

  private String getJavaExecutable() {
    String javaHome = System.getProperty("java.home");
    return javaHome + File.separator + "bin" + File.separator + "java";
  }

  /**
   * Writes a Java {@code @}-argfile containing {@code -cp <classpath> <mainClass> <args...>},
   * so the forked command line stays short regardless of how long the resolved classpath is.
   */
  private File createArgFile(String classpath, String... processorArgs) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("-cp");
    lines.add(quoteArgFileToken(classpath));
    lines.add(getGmdProcessorClassName());
    for (String arg : processorArgs) {
      lines.add(quoteArgFileToken(arg));
    }
    File argFile = File.createTempFile("gmd-classpath", ".args");
    argFile.deleteOnExit();
    Files.write(argFile.toPath(), lines, StandardCharsets.UTF_8);
    return argFile;
  }

  private static String quoteArgFileToken(String value) {
    String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
    return "\"" + escaped + "\"";
  }

  /**
   * Returns the entry point used by the forked GMD process.
   *
   * @return the fully qualified GmdProcessor class name
   */
  protected String getGmdProcessorClassName() {
    return se.alipsa.gmd.core.GmdProcessor.class.getName();
  }
}
