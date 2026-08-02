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
    extension.gmdVersion.convention('3.1.0')
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
