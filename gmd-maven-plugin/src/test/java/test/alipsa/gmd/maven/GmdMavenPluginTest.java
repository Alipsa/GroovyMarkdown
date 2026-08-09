package test.alipsa.gmd.maven;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.execution.MavenSession;
import org.junit.jupiter.api.Test;
import se.alipsa.gmd.maven.GmdMavenPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@MojoTest
public class GmdMavenPluginTest {

  @Test
  @InjectMojo(goal = "processGmd", pom = "src/test/projects/pom.xml")
  public void testGmdMavenPlugin(GmdMavenPlugin plugin) throws Exception {
    File pomFile = new File("src/test/projects/");
    assertTrue(pomFile.exists());
    String expectedVersion = System.getProperty("gmd.plugin.version");
    assertNotNull(expectedVersion);
    assertNotNull(plugin.getGmdVersion());
    assertEquals(expectedVersion, plugin.getGmdVersion());

    // Execute the plugin
    plugin.execute();

    // Verify that the output files were created
    File targetDir = new File(plugin.getTargetDir());
    assertTrue(targetDir.exists());
    assertTrue(targetDir.isDirectory());

    File testHtml = new File(targetDir, "test.html");
    assertTrue(testHtml.exists());
    var testContent = Files.readString(testHtml.toPath());
    assertTrue(testContent.contains("<h1>Greetings</h1>"));
    assertTrue(testContent.contains("Hello world!"));

    File testInline = new File(targetDir, "inline.html");
    assertTrue(testInline.exists());
    var testInlineHtml = Files.readString(testInline.toPath());
    assertTrue(testInlineHtml.contains("<h1>Inline</h1>"));
    assertTrue(testInlineHtml.contains("Today is "));
    assertTrue(testInlineHtml.contains(" and the time is "));
  }

  @Test
  @InjectMojo(goal = "processGmd", pom = "src/test/projects/pom.xml")
  public void testGmdMavenPluginWithResolvedDependencies(GmdMavenPlugin plugin) throws Exception {
    RepositorySystem repositorySystem = Mockito.mock(RepositorySystem.class);
    RepositorySystemSession repositorySession = Mockito.mock(RepositorySystemSession.class);
    LocalRepositoryManager localRepositoryManager = Mockito.mock(LocalRepositoryManager.class);
    MavenSession session = Mockito.mock(MavenSession.class);
    DependencyResult dependencyResult = new DependencyResult(new DependencyRequest());

    when(session.getRepositorySession()).thenReturn(repositorySession);
    when(repositorySession.getLocalRepositoryManager()).thenReturn(localRepositoryManager);
    when(repositorySystem.resolveDependencies(any(RepositorySystemSession.class), any(DependencyRequest.class)))
        .thenReturn(dependencyResult);

    List<ArtifactResult> artifactResults = Arrays.stream(System.getProperty("java.class.path")
            .split(java.util.regex.Pattern.quote(File.pathSeparator)))
        .map(path -> {
          Artifact artifact = Mockito.mock(Artifact.class);
          when(artifact.getFile()).thenReturn(new File(path));
          return new ArtifactResult(new ArtifactRequest()).setArtifact(artifact);
        })
        .toList();
    dependencyResult.setArtifactResults(artifactResults);

    setField(plugin, "repositorySystem", repositorySystem);
    setField(plugin, "session", session);
    setField(plugin, "targetDir", "target/gmd-resolved");
    File outputDirectory = new File(plugin.getTargetDir());
    deleteDirectory(outputDirectory);
    assertFalse(outputDirectory.exists(), "Could not clear resolved-dependency test output");

    try {
      plugin.execute();

      File testHtml = new File(plugin.getTargetDir(), "test.html");
      assertTrue(testHtml.isFile(), "The resolved-dependency fork did not write test.html");
      assertTrue(Files.readString(testHtml.toPath()).contains("<h1>Greetings</h1>"));
      Mockito.verify(repositorySystem).resolveDependencies(any(RepositorySystemSession.class), any(DependencyRequest.class));
    } finally {
      deleteDirectory(outputDirectory);
    }
  }

  private static void deleteDirectory(File directory) throws IOException {
    if (!directory.exists()) {
      return;
    }
    try (var paths = Files.walk(directory.toPath())) {
      try {
        paths.sorted(Comparator.reverseOrder()).forEach(path -> {
          try {
            Files.deleteIfExists(path);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
      } catch (UncheckedIOException e) {
        throw e.getCause();
      }
    }
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

}
