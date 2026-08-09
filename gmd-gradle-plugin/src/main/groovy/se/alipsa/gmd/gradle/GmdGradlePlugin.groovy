package se.alipsa.gmd.gradle

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.tasks.TaskProvider

import java.io.IOException
import java.io.InputStream
import java.util.Properties

@CompileStatic
class GmdGradlePlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    GmdGradlePluginParams extension = project.extensions.create('gmdPlugin', GmdGradlePluginParams)
    extension.sourceDir.convention('src/main/gmd')
    extension.targetDir.convention('build/gmd')
    extension.outputType.convention('md')
    extension.groovyVersion.convention('5.0.8')
    extension.log4jVersion.convention('2.26.1')
    extension.gmdVersion.convention(project.providers.provider { defaultGmdVersion() })
    extension.ivyVersion.convention('2.6.0')
    extension.runTaskBefore.convention('test')

    TaskProvider<ProcessGmdTask> processGmdTask = project.tasks.register('processGmd', ProcessGmdTask)

    project.afterEvaluate {
      String sourceDir = extension.sourceDir.get()
      String targetDir = extension.targetDir.get()
      String outputType = extension.outputType.get()
      Configuration configuration = addDependencies(project,
          extension.groovyVersion.get(),
          extension.log4jVersion.get(),
          extension.gmdVersion.get(),
          extension.ivyVersion.get()
      )

      processGmdTask.configure { ProcessGmdTask task ->
        // Resolve all project values during configuration. The task action only
        // uses task properties and injected services, which enables the
        // configuration cache and parallel task execution.
        task.sourceDir.set(project.file(sourceDir))
        task.targetDir.set(project.file(targetDir))
        task.outputType.set(outputType)
        task.classpath.from(configuration)
      }

      try {
        TaskProvider<Task> buildTask = project.tasks.named(extension.runTaskBefore.get())
        buildTask.configure { Task task ->
          task.dependsOn(processGmdTask)
        }
      } catch (Exception e) {
        project.logger.warn("Could not add processGmd task before the test task: ${e.message}")
      }
    }
  }

  private static String defaultGmdVersion() {
    InputStream stream = GmdGradlePlugin.class.getResourceAsStream('/gmd-version.properties')
    if (stream == null) {
      throw new IllegalStateException(
          'GMD core version metadata is missing from the Gradle plugin; set gmdPlugin.gmdVersion explicitly'
      )
    }
    try {
      Properties properties = new Properties()
      properties.load(stream)
      String version = properties.getProperty('gmd.version')
      String normalizedVersion = version == null ? null : version.trim()
      if (normalizedVersion == null || normalizedVersion.isEmpty()
          || normalizedVersion.contains('$') || normalizedVersion.contains('{')) {
        throw new IllegalStateException(
            'GMD core version metadata is invalid; set gmdPlugin.gmdVersion explicitly'
        )
      }
      return normalizedVersion
    } catch (IOException e) {
      throw new IllegalStateException('Could not read GMD core version metadata', e)
    } finally {
      try {
        stream.close()
      } catch (IOException ignored) {
        // Ignore cleanup failures while resolving the version resource.
      }
    }
  }

  static Configuration addDependencies(Project project,
                                       String groovyVersion, String log4jVersion, String gmdVersion,
                                       String ivyVersion) {
    MavenArtifactRepository mavenCentral = project.repositories.mavenCentral()
    if (!hasRepository(project, mavenCentral)) {
      project.repositories.add(mavenCentral)
    }

    List<Dependency> dependencies = [
        project.dependencies.create("org.apache.groovy:groovy:${groovyVersion}"),
        project.dependencies.create("org.apache.groovy:groovy-templates:${groovyVersion}"),
        project.dependencies.create("org.apache.groovy:groovy-jsr223:${groovyVersion}"),
        project.dependencies.create("org.apache.ivy:ivy:${ivyVersion}"), // needed for @Grab)
        project.dependencies.create("org.apache.logging.log4j:log4j-core:${log4jVersion}"),
        project.dependencies.create("se.alipsa.gmd:gmd-core:${gmdVersion}")
    ]

    return project.configurations.detachedConfiguration(dependencies.toArray(new Dependency[0]))
  }

  static boolean hasRepository(Project project, MavenArtifactRepository repo) {
    return project.repositories.find {
      it instanceof MavenArtifactRepository && it.url == repo.url
    } != null
  }
}
