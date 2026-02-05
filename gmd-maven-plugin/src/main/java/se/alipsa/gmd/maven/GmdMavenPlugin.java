package se.alipsa.gmd.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
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

  @Parameter(name = "groovyVersion", property = "processGmd.groovyVersion", defaultValue = "5.0.4")
  private String groovyVersion;

  @Parameter(name = "log4jVersion", property = "processGmd.log4jVersion", defaultValue = "2.25.3")
  private String log4jVersion;

  @Parameter(name = "gmdVersion", property = "processGmd.gmdVersion", defaultValue = "3.0.2")
  private String gmdVersion;

  @Parameter(name = "ivyVersion", property = "processGmd.ivyVersion", defaultValue = "2.5.3")
  private String ivyVersion;

  @Parameter(name = "javaFxVersion", property = "processGmd.javaFxVersion", defaultValue = "23.0.2")
  private String javaFxVersion;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Component
  private RepositorySystem repositorySystem;

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
   * The version of Groovy to use. Default is 5.0.4
   *
   * @return The version of Groovy to use.
   */
  public String getGroovyVersion() {
    return groovyVersion;
  }

  /**
   * The version of Log4j to use. Default is 2.25.3
   *
   * @return The version of Log4j to use.
   */
  public String getLog4jVersion() {
    return log4jVersion;
  }

  /**
   * The version of GMD core to use. Default is 3.0.2
   *
   * @return The version of GMD core to use.
   */
  public String getGmdVersion() {
    return gmdVersion;
  }

  /**
   * The version of Ivy to use. Default is 2.5.3
   *
   * @return The version of Ivy to use.
   */
  public String getIvyVersion() {
    return ivyVersion;
  }

  /**
   * The version of JavaFX to use. Default is 23.0.2
   *
   * @return The version of JavaFX to use.
   */
  public String getJavaFxVersion() {
    return javaFxVersion;
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      File srcDir = new File(sourceDir);
      if (!srcDir.exists()) {
        getLog().warn("Source directory " + sourceDir + " does not exist, nothing to do");
        return;
      }
      if (srcDir.isFile()) {
        throw new MojoFailureException(sourceDir + " is a file, not a directory");
      }
      if (Objects.requireNonNull(srcDir.list()).length == 0) {
        getLog().warn("No gmd files found in " + sourceDir + ", nothing to do");
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

        // Execute GmdProcessor in a forked process with custom classpath
        List<String> command = new ArrayList<>();
        command.add(getJavaExecutable());
        command.add("-cp");
        command.add(classpath.toString());
        command.add("se.alipsa.gmd.core.GmdProcessor");
        command.add(srcDir.getCanonicalPath());
        command.add(new File(targetDir).getCanonicalPath());
        command.add(outputType);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
          throw new MojoFailureException("GmdProcessor exited with code " + exitCode);
        }
      } else {
        // Fall back to using GmdProcessor directly with bundled dependencies
        getLog().warn("Cannot resolve custom dependencies, using bundled versions");
        se.alipsa.gmd.core.GmdProcessor gmdProcessor = new se.alipsa.gmd.core.GmdProcessor();
        gmdProcessor.process(sourceDir, targetDir, outputType);
      }

      File td = new File(targetDir);
      if (td.exists()) {
        getLog().info("Gmd files processed and written to " + td.getCanonicalPath());
      } else {
        getLog().warn(td.getCanonicalPath() + " should exists but does not, something is probably wrong");
      }
    } catch (DependencyResolutionException e) {
      throw new MojoExecutionException("Failed to resolve dependencies", e);
    } catch (Exception e) {
      throw new MojoFailureException("Failed to process gmd files in " + sourceDir, e);
    }
  }

  private List<File> resolveDependencies() throws DependencyResolutionException {
    RepositorySystemSession repoSession = session.getRepositorySession();

    // Determine platform classifier for JavaFX
    String osName = System.getProperty("os.name").toLowerCase();
    String osArch = System.getProperty("os.arch").toLowerCase();
    String platform;
    if (osName.contains("mac") || osName.contains("darwin")) {
      platform = (osArch.contains("aarch64") || osArch.contains("arm")) ? "mac-aarch64" : "mac";
    } else if (osName.contains("linux")) {
      platform = "linux";
    } else if (osName.contains("win")) {
      platform = "win";
    } else {
      throw new IllegalStateException("Unsupported OS: " + osName);
    }

    List<Dependency> dependencies = new ArrayList<>();
    dependencies.add(new Dependency(new DefaultArtifact("org.apache.groovy:groovy:" + groovyVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.apache.groovy:groovy-templates:" + groovyVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.apache.groovy:groovy-jsr223:" + groovyVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.apache.ivy:ivy:" + ivyVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.apache.logging.log4j:log4j-core:" + log4jVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("se.alipsa.gmd:gmd-core:" + gmdVersion), "runtime"));

    // JavaFX modules with platform-specific classifiers
    dependencies.add(new Dependency(new DefaultArtifact("org.openjfx", "javafx-base", platform, "jar", javaFxVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.openjfx", "javafx-graphics", platform, "jar", javaFxVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.openjfx", "javafx-controls", platform, "jar", javaFxVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.openjfx", "javafx-swing", platform, "jar", javaFxVersion), "runtime"));
    dependencies.add(new Dependency(new DefaultArtifact("org.openjfx", "javafx-web", platform, "jar", javaFxVersion), "runtime"));

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

  private String getJavaExecutable() {
    String javaHome = System.getProperty("java.home");
    return javaHome + File.separator + "bin" + File.separator + "java";
  }
}
