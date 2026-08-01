package test.alipsa.gmd.maven;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.junit.jupiter.api.Test;
import se.alipsa.gmd.maven.GmdMavenPlugin;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MojoTest
public class GmdMavenPluginTest {

  @Test
  @InjectMojo(goal = "processGmd", pom = "src/test/projects/pom.xml")
  public void testGmdMavenPlugin(GmdMavenPlugin plugin) throws Exception {
    File pomFile = new File("src/test/projects/");
    assertTrue(pomFile.exists());

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

}
