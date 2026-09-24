package test.alipsa.gmd.gradle

import groovy.ant.AntBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import se.alipsa.gmd.gradle.GmdGradlePlugin
import se.alipsa.gmd.gradle.ProcessGmdTask

import java.util.Properties

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class GmdGradlePluginTest {

  private static String rootPomRevision() {
    def factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
    factory.setFeature('http://xml.org/sax/features/external-general-entities', false)
    factory.setFeature('http://xml.org/sax/features/external-parameter-entities', false)
    factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, '')
    factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, '')
    factory.setXIncludeAware(false)
    factory.setExpandEntityReferences(false)
    String rootPomPath = System.getProperty('gmd.root.pom')
    Assertions.assertNotNull(rootPomPath, 'The root POM path must be provided by the Gradle test task')
    def document = factory.newDocumentBuilder().parse(new File(rootPomPath))
    def project = document.documentElement
    def properties = directElementChild(project, 'properties')
    def revision = directElementChild(properties, 'revision')
    Assertions.assertNotNull(revision, 'The root POM must define project/properties/revision')
    revision.getTextContent().trim()
  }

  private static org.w3c.dom.Node directElementChild(org.w3c.dom.Node parent, String localName) {
    Assertions.assertNotNull(parent, "Expected a parent element containing direct $localName")
    for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
      def child = parent.getChildNodes().item(i)
      if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && child.getLocalName() == localName) {
        return child
      }
    }
    return null
  }

  @Test
  void defaultGmdVersionComesFromGeneratedResource() {
    URL resource = ProcessGmdTask.class.getResource('/gmd-version.properties')
    Assertions.assertNotNull(resource, 'The plugin version resource must be generated during processResources')

    Properties properties = new Properties()
    resource.withInputStream { properties.load(it) }
    String resourceVersion = properties.getProperty('gmd.version')
    String expectedVersion = System.getProperty('gmd.plugin.version')
    String publishVersion = System.getProperty('gmd.publish.version')
    Assertions.assertNotNull(expectedVersion)
    Assertions.assertNotNull(resourceVersion)
    if (!publishVersion) {
      Assertions.assertEquals(rootPomRevision(), expectedVersion)
    }
    Assertions.assertEquals(expectedVersion, resourceVersion)
    Assertions.assertFalse(resourceVersion.contains('$'), "The generated resource must be expanded: $resourceVersion")

    def method = ProcessGmdTask.class.getDeclaredMethod('defaultGmdVersion')
    method.setAccessible(true)
    Assertions.assertEquals(resourceVersion, method.invoke(null))
  }

  @Test
  void targetDirIsDefaultGmdOutputHasASafeDefaultForDirectRegistration() {
    // Registering the task directly (bypassing GmdGradlePlugin's afterEvaluate
    // wiring, which is the only place targetDirIsDefaultGmdOutput is normally set)
    // must not leave this @Input property without a value.
    def project = ProjectBuilder.builder().build()
    ProcessGmdTask task = project.tasks.register('siteGmd', ProcessGmdTask).get()

    Assertions.assertFalse(task.targetDirIsDefaultGmdOutput.get(),
        'targetDirIsDefaultGmdOutput should default to false when set only by convention')
  }

  @Test
  void runtimeClasspathIsTrackedAsAClasspathInput() {
    def project = ProjectBuilder.builder().build()
    ProcessGmdTask task = project.tasks.register('siteGmd', ProcessGmdTask).get()
    File sourceDir = new File(project.projectDir, 'src/gmd')
    sourceDir.mkdirs()
    new File(sourceDir, 'test.gmd').text = '# Greetings\n'
    File runtimeJar = new File(project.buildDir, 'runtime.jar')
    runtimeJar.parentFile.mkdirs()
    runtimeJar.createNewFile()
    task.sourceDir.set(sourceDir)
    task.runtimeClasspath.from(runtimeJar)

    Assertions.assertTrue(task.inputs.files.files.contains(runtimeJar),
        'The resolved processor runtime must participate in up-to-date checks')
    Assertions.assertFalse(task.inputs.properties.containsKey('classpathIdentity'),
        'A declared-version identity must not replace the actual runtime classpath')
  }

  @Test
  void mavenCentralIsAddedOnlyWhenTheProjectHasNone() {
    def project = ProjectBuilder.builder().build()
    Assertions.assertEquals(0, project.repositories.size())

    GmdGradlePlugin.addDependencies(project, '5.1.3', '2.26.1', '3.1.0', '2.6.0')

    Assertions.assertEquals(1, project.repositories.size())
  }

  @Test
  void anExistingMavenCentralIsNotDuplicated() {
    def project = ProjectBuilder.builder().build()
    project.repositories.mavenCentral()

    GmdGradlePlugin.addDependencies(project, '5.1.3', '2.26.1', '3.1.0', '2.6.0')

    Assertions.assertEquals(1, project.repositories.size())
  }

  @Test
  void mavenCentralIsRecognisedUnderItsOtherHost() {
    def project = ProjectBuilder.builder().build()
    project.repositories.maven { it.setUrl('https://repo1.maven.org/maven2') }

    GmdGradlePlugin.addDependencies(project, '5.1.3', '2.26.1', '3.1.0', '2.6.0')

    Assertions.assertEquals(1, project.repositories.size())
  }

  @Test
  void aPrivateMirrorStillGetsMavenCentralAdded() {
    def project = ProjectBuilder.builder().build()
    project.repositories.maven { it.setUrl('https://nexus.example.com/repository/maven-public') }

    GmdGradlePlugin.addDependencies(project, '5.1.3', '2.26.1', '3.1.0', '2.6.0')

    Assertions.assertEquals(2, project.repositories.size())
  }

  @Test
  void settingsManagedRepositoriesDoNotBreakTheBuild() {
    File testProjectDir = new File('build/gmdSettingsReposTest')
    try {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
      File srcDir = new File(testProjectDir, 'src/test/gmd')
      srcDir.mkdirs()
      new File(srcDir, 'test.gmd').text = '# Greetings\n'
      new File(testProjectDir, 'settings.gradle').text = '''
      pluginManagement {
          repositories { mavenCentral() }
          plugins { id 'se.alipsa.gmd.gmd-gradle-plugin' version '1.0.0' }
      }
      dependencyResolutionManagement {
          repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
          repositories { mavenCentral() }
      }
      '''.stripIndent()
      new File(testProjectDir, 'build.gradle').text = '''
      plugins {
          id('base')
          id 'se.alipsa.gmd.gmd-gradle-plugin'
      }
      gmdPlugin {
          sourceDir = 'src/test/gmd'
          targetDir = 'build/target'
          outputType = 'html'
          gmdVersion = '3.1.0' // Keep the standalone TestKit test independent of unpublished snapshots.
          runTaskBefore = 'build'
      }
      '''.stripIndent()

      def result = GradleRunner.create().withProjectDir(testProjectDir)
          .withArguments('processGmd').withPluginClasspath().forwardOutput().build()

      Assertions.assertEquals(SUCCESS, result.task(':processGmd').outcome, result.output)
      Assertions.assertTrue(new File(testProjectDir, 'build/target/test.html').exists())
    } finally {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
    }
  }

  @Test
  void preferSettingsRepositoriesAreNotMutatedByTheProject() {
    // Copilot review on PR #11: under PREFER_SETTINGS, project.repositories.mavenCentral()
    // is not rejected with InvalidUserCodeException the way it is under
    // FAIL_ON_PROJECT_REPOS - Gradle silently accepts (and deprecates) it instead. Verify
    // the plugin detects PREFER_SETTINGS up front and never adds to project.repositories,
    // so the build carries no deprecation warning and the settings repositories are what
    // actually resolve gmd-core. ProjectBuilder cannot model dependencyResolutionManagement,
    // so this is a TestKit test.
    File testProjectDir = new File('build/gmdPreferSettingsReposTest')
    try {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
      File srcDir = new File(testProjectDir, 'src/test/gmd')
      srcDir.mkdirs()
      new File(srcDir, 'test.gmd').text = '# Greetings\n'

      new File(testProjectDir, 'settings.gradle').text = '''
      pluginManagement {
          repositories { mavenCentral() }
          plugins { id 'se.alipsa.gmd.gmd-gradle-plugin' version '1.0.0' }
      }
      dependencyResolutionManagement {
          repositoriesMode = RepositoriesMode.PREFER_SETTINGS
          repositories { mavenCentral() }
      }
      '''.stripIndent()

      // Deliberately no project-level repositories block.
      new File(testProjectDir, 'build.gradle').text = '''
      plugins {
          id('base')
          id 'se.alipsa.gmd.gmd-gradle-plugin'
      }
      gmdPlugin {
          sourceDir = 'src/test/gmd'
          targetDir = 'build/target'
          outputType = 'html'
          gmdVersion = '3.1.0'
          runTaskBefore = 'build'
      }
      tasks.register('printRepoCount') {
          doLast { println "repoCount=" + project.repositories.size() }
      }
      '''.stripIndent()

      def result = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('processGmd', 'printRepoCount')
          .withPluginClasspath()
          .forwardOutput()
          .build()

      Assertions.assertEquals(SUCCESS, result.task(':processGmd').outcome,
          "PREFER_SETTINGS must not break the plugin:\n${result.output}")
      Assertions.assertTrue(new File(testProjectDir, 'build/target/test.html').exists(),
          'The processor runtime must still resolve through the settings repositories')
      Assertions.assertTrue(result.output.contains('repoCount=0'),
          "The plugin must not add to project.repositories under PREFER_SETTINGS:\n${result.output}")
      Assertions.assertFalse(
          result.output.contains('prefer settings repositories over project repositories'),
          "The plugin must not trigger Gradle's PREFER_SETTINGS deprecation warning:\n${result.output}")
    } finally {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
    }
  }

  @Test
  void aSourceDirWithoutGmdFilesDoesNotResolveTheRuntime() {
    File testProjectDir = new File('build/gmdNoGmdFilesTest')
    try {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
      File srcDir = new File(testProjectDir, 'src/test/gmd')
      srcDir.mkdirs()
      new File(srcDir, 'readme.txt').text = 'not a gmd file'
      new File(testProjectDir, 'settings.gradle').text = '''
      pluginManagement {
          repositories { mavenCentral() }
          plugins { id 'se.alipsa.gmd.gmd-gradle-plugin' version '1.0.0' }
      }
      '''.stripIndent()
      new File(testProjectDir, 'build.gradle').text = '''
      plugins {
          id('base')
          id 'se.alipsa.gmd.gmd-gradle-plugin'
      }
      repositories { mavenCentral() }
      gmdPlugin {
          sourceDir = 'src/test/gmd'
          targetDir = 'build/target'
          outputType = 'html'
          gmdVersion = 'not-a-real-gmd-version'
          runTaskBefore = 'build'
      }
      '''.stripIndent()

      def helpResult = GradleRunner.create().withProjectDir(testProjectDir)
          .withArguments('help', '--configuration-cache', '--offline')
          .withPluginClasspath().forwardOutput().build()

      Assertions.assertEquals(SUCCESS, helpResult.task(':help').outcome,
          "Configuration-cache storage must not resolve the unavailable runtime:\n${helpResult.output}")

      def result = GradleRunner.create().withProjectDir(testProjectDir)
          .withArguments('processGmd', '--configuration-cache', '--offline')
          .withPluginClasspath().forwardOutput().build()

      Assertions.assertTrue(result.output.contains('No gmd files found in'), result.output)
      Assertions.assertFalse(result.output.contains('Gmd files processed and written to'), result.output)
      Assertions.assertFalse(new File(testProjectDir, 'build/target').exists(), result.output)
    } finally {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
    }
  }

  @Test
  void directlyRegisteredTaskInvalidatesWhenItsRuntimeClasspathChanges() {
    // End-to-end proof of the fix for silent stale up-to-date checks on a
    // directly registered task: its actual runtime classpath must invalidate
    // the task when a dependency changes. The swap
    // changes only log4j-core so the runnable gmd-core:3.1.0 stays in place
    // (pre-3.1.0 gmd-core releases carry a JavaFX dependency graph).
    File testProjectDir = new File('build/gmdDirectTaskIdentityTest')
    try {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
      File srcDir = new File(testProjectDir, 'src/test/gmd')
      srcDir.mkdirs()
      new File(srcDir, 'test.gmd').text = '# Greetings\n'
      // Own settings file: the test project lives under this build's directory,
      // so without one Gradle would adopt the plugin build's settings.gradle.
      new File(testProjectDir, 'settings.gradle').text = "rootProject.name = 'gmd-direct-task-identity'\n"
      File buildFile = new File(testProjectDir, 'build.gradle')
      buildFile.text = '''
      import se.alipsa.gmd.gradle.ProcessGmdTask

      plugins {
          id('base')
          // Applied for its classes only; the task below is registered directly,
          // bypassing GmdGradlePlugin's processGmd wiring.
          id 'se.alipsa.gmd.gmd-gradle-plugin'
      }

      repositories { mavenCentral() }

      def log4jVersion = '2.26.1'
      // Mirror the runtime GmdGradlePlugin assembles: gmd-core's published POM
      // does not bring Groovy transitively.
      def gmdRuntime = configurations.create('directGmdRuntime')
      gmdRuntime.canBeConsumed = false
      gmdRuntime.canBeResolved = true
      gmdRuntime.dependencies.addAll([
          dependencies.create('se.alipsa.gmd:gmd-core:3.1.0'),
          dependencies.create('org.apache.groovy:groovy:5.1.3'),
          dependencies.create('org.apache.groovy:groovy-templates:5.1.3'),
          dependencies.create('org.apache.groovy:groovy-jsr223:5.1.3'),
          dependencies.create('org.apache.ivy:ivy:2.6.0'),
          dependencies.create("org.apache.logging.log4j:log4j-core:${log4jVersion}")
      ])

      tasks.register('directGmd', ProcessGmdTask) {
          sourceDir = file('src/test/gmd')
          targetDir = file('build/target')
          outputType = 'html'
          runtimeClasspath.from(gmdRuntime)
      }
      '''.stripIndent()

      def result = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('directGmd')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      Assertions.assertEquals(SUCCESS, result.task(':directGmd').outcome, result.output)
      Assertions.assertTrue(new File(testProjectDir, 'build/target/test.html').exists())

      def cachedResult = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('directGmd')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      Assertions.assertEquals(org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE,
          cachedResult.task(':directGmd').outcome,
          'An unchanged runtime must keep the task up-to-date:\n' + cachedResult.output)

      buildFile.text = buildFile.text.replace(
          "def log4jVersion = '2.26.1'", "def log4jVersion = '2.25.1'")
      def changedRuntimeResult = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('directGmd')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      Assertions.assertEquals(SUCCESS, changedRuntimeResult.task(':directGmd').outcome,
          'Swapping the runtime classpath must rerun a directly registered task:\n'
              + changedRuntimeResult.output)
    } finally {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
    }
  }

  @Test
  void testPlugin() {
    File targetDir = null
    File testProjectDir = new File('build/gmdPluginTest')
    try {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
      testProjectDir.mkdirs()
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
            mavenCentral()
        }
        gmdPlugin {
            sourceDir = 'src/test/gmd'
            targetDir = 'build/target'
            outputType = 'html'
            gmdVersion = '3.1.0' // Keep the standalone TestKit test independent of unpublished snapshots.
            log4jVersion = '2.26.1' // set explicitly so the invalidation check below can swap it
            runTaskBefore = 'build' // we dont have tests so specify the task to not get a warning
        }
        """.stripIndent()

      def settingsFile = new File(testProjectDir, 'settings.gradle')
      settingsFile.text = """
      pluginManagement {
          repositories {
              mavenCentral()
          }
          plugins {
              id 'se.alipsa.gmd.gmd-gradle-plugin' version "1.0.0"
          }
      }
      """.stripIndent()

      def result = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('processGmd', '--configuration-cache', '--parallel')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      assert result.task(":processGmd").outcome == SUCCESS

      def cachedResult = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('processGmd', '--configuration-cache', '--parallel')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      Assertions.assertEquals(org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE, cachedResult.task(":processGmd").outcome,
          'A custom target must retain Gradle up-to-date checks')

      // This proves end-to-end that the resolved classpath invalidates the task
      // when the plugin changes a runtime dependency version.
      buildFile.text = buildFile.text.replace("log4jVersion = '2.26.1'", "log4jVersion = '2.25.1'")
      def changedVersionResult = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('processGmd', '--configuration-cache', '--parallel')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      Assertions.assertEquals(SUCCESS, changedVersionResult.task(':processGmd').outcome,
          'Changing a runtime dependency version must rerun processGmd')

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
      Assertions.assertTrue(staleOutput.exists(),
          'A custom target directory must retain files that processGmd did not generate')
      Assertions.assertTrue(testHtml.delete(), 'The generated test output should be removable for this check')
      def restoredOutputResult = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('processGmd', '--configuration-cache', '--parallel')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      Assertions.assertEquals(SUCCESS, restoredOutputResult.task(':processGmd').outcome,
          'A missing generated file in a custom target must rerun processGmd')
      Assertions.assertTrue(testHtml.exists(), 'processGmd must restore a missing generated output')
    } catch (Exception e) {
      println("Files are in ${targetDir?.absolutePath}")
      throw e
    } finally {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
    }
  }

  @Test
  void defaultTargetRemovesStaleGeneratedFiles() {
    File targetDir = null
    File testProjectDir = new File('build/gmdPluginDefaultTargetTest')
    try {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
      testProjectDir.mkdirs()
      File srcDir = new File(testProjectDir, 'src/test/gmd')
      srcDir.mkdirs()
      targetDir = new File(testProjectDir, 'build/gmd')
      targetDir.mkdirs()
      File staleOutput = new File(targetDir, 'stale.html')
      staleOutput.text = 'stale generated output'
      File gmdFile = new File(srcDir, 'test.gmd')
      gmdFile.text = '# Greetings'

      new File(testProjectDir, 'build.gradle').text = '''
        plugins {
            id('base')
            id 'se.alipsa.gmd.gmd-gradle-plugin'
        }
        repositories {
            mavenCentral()
        }
        gmdPlugin {
            sourceDir = 'src/test/gmd'
            outputType = 'html'
            gmdVersion = '3.1.0'
            runTaskBefore = 'build'
        }
      '''.stripIndent()
      new File(testProjectDir, 'settings.gradle').text = '''
        pluginManagement {
          repositories {
              mavenCentral()
          }
          plugins {
              id 'se.alipsa.gmd.gmd-gradle-plugin' version '1.0.0'
          }
        }
      '''.stripIndent()

      def result = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('processGmd', '--configuration-cache', '--parallel')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      Assertions.assertEquals(SUCCESS, result.task(':processGmd').outcome)
      Assertions.assertFalse(staleOutput.exists(), 'The default dedicated target should remove stale GMD output')
      Assertions.assertTrue(new File(targetDir, 'test.html').exists(), 'The GMD source should be processed')

      def cachedResult = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('processGmd', '--configuration-cache', '--parallel')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      Assertions.assertEquals(org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE, cachedResult.task(':processGmd').outcome,
          'The default target must retain Gradle up-to-date checks')

      File generatedOutput = new File(targetDir, 'test.html')
      Assertions.assertTrue(gmdFile.delete(), 'The GMD source should be removable for this check')
      def cleanupResult = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withArguments('processGmd', '--configuration-cache', '--parallel')
          .withPluginClasspath()
          .forwardOutput()
          .build()
      Assertions.assertEquals(SUCCESS, cleanupResult.task(':processGmd').outcome)
      Assertions.assertFalse(generatedOutput.exists(), 'Deleting a GMD source must remove its stale generated output')
      Assertions.assertTrue(cleanupResult.output.contains('Removed stale generated GMD output'),
          "processGmd must remove the orphan itself:\n${cleanupResult.output}")
    } catch (Exception e) {
      println("Files are in ${targetDir?.absolutePath}")
      throw e
    } finally {
      new AntBuilder().delete(dir: testProjectDir, failonerror: false)
    }
  }
}
