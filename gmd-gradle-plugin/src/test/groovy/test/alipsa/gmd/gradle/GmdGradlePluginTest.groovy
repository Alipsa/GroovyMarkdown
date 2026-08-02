package test.alipsa.gmd.gradle

import groovy.ant.AntBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.gradle.testkit.runner.GradleRunner
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class GmdGradlePluginTest {

  @Test
  void testPlugin() {
    File targetDir = null
    try {
      File testProjectDir = new File("build/gmdPluginTest")
      if (!testProjectDir.exists()) {
        testProjectDir.mkdirs()
      }
      File srcDir = new File(testProjectDir, 'src/test/gmd')
      srcDir.mkdirs()
      targetDir = new File(testProjectDir, 'build/target')
      targetDir.mkdirs()
      File staleOutput = new File(targetDir, 'stale.html')
      staleOutput.text = 'stale generated output'

      def gmdFile = new File(srcDir, 'test.gmd')
      gmdFile.text = """
      # Greetings
  
      ```{groovy echo=false}
      out.println "Hello world!"
      ```
      """.stripIndent()

      def gmdFile2 = new File(srcDir, 'inline.gmd')
      gmdFile2.text = """
      # Inline
      
      ```{groovy}
      import java.time.LocalDateTime
      import java.time.format.DateTimeFormatter
      
      now = LocalDateTime.now()
      f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      ```
      Today is `= f.format(now)` and the time is `= now.getMinute()` past `= now.getHour()`.
      """.stripIndent()

      def buildFile = new File(testProjectDir, 'build.gradle')
      buildFile.text = """
        plugins {
            id('base') // add build and assemble tasks
            id 'se.alipsa.gmd.gmd-gradle-plugin'
        }
        group = 'test.alipsa.gmd'
        version = '1.0.0-SNAPSHOT'
        repositories {
            // Enable us to to use local snapshots
            mavenLocal()
        }
        gmdPlugin {
            sourceDir = 'src/test/gmd'
            targetDir = 'build/target'
            outputType = 'html'
            runTaskBefore = 'build' // we dont have tests so specify the task to not get a warning 
        }
        """.stripIndent()

      def settingsFile = new File(testProjectDir, 'settings.gradle')
      settingsFile.text = """
      pluginManagement {
          repositories {
              mavenLocal()
          }
          plugins {
              id 'se.alipsa.gmd.gmd-gradle-plugin' version "1.0.0"
          }
      }
      """.stripIndent()

      def result = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('processGmd')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      assert result.task(":processGmd").outcome == SUCCESS

      // the directory differs on a mac even though they point to the same place so cannot include
      def expected = "Gmd files processed and written to $targetDir.canonicalPath".toString()
      Assertions.assertTrue(result.output.contains(expected), "expected \n$expected, but output was ${result.output}")

      def testHtml = new File(targetDir, 'test.html')
      assert testHtml.exists()
      assert testHtml.text.contains("<h1>Greetings</h1>")
      assert testHtml.text.contains("Hello world!")

      def testInlineHtml = new File(targetDir, 'inline.html')
      assert testInlineHtml.exists()
      assert testInlineHtml.text.contains("<h1>Inline</h1>")
      assert testInlineHtml.text.contains("Today is ")
      assert testInlineHtml.text.contains(" and the time is ")
      Assertions.assertFalse(staleOutput.exists(), 'Stale generated output should be removed')
      // cleanup
      AntBuilder ant = new AntBuilder()
      ant.delete(dir: testProjectDir, failonerror: false)
    } catch (Exception e) {
      println("Files are in ${targetDir?.absolutePath}")
      throw e
    }
  }
}
