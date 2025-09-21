package se.alipsa.gmd.gradle

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations

import javax.inject.Inject

@CompileStatic
class GmdGradlePlugin implements Plugin<Project> {

  ExecOperations execOperations

  @Inject
  GmdGradlePlugin(ExecOperations execOperations) {
    this.execOperations = execOperations
  }

  @Override
  void apply(Project project) {
    def extension = project.extensions.create('gmdPlugin', GmdGradlePluginParams)

    TaskProvider<Task> processGmdTask = project.tasks.register('processGmd') {
      it.doLast {
        def sourceDir= project.file(extension.sourceDir.getOrElse("src/main/gmd"))
        def targetDir= project.file(extension.targetDir.getOrElse("build/gmd"))
        def outputType= extension.outputType.getOrElse('md')
        def groovyVersion = extension.groovyVersion.getOrElse('5.0.1')
        def log4jVersion = extension.log4jVersion.getOrElse('2.24.3')
        def gmdVersion = extension.gmdVersion.getOrElse('3.0.1')
        def ivyVersion = extension.ivyVersion.getOrElse('2.5.3')

        if (!sourceDir.exists()) {
          project.logger.warn("Source directory ${sourceDir.canonicalPath} does not exist, nothing to do")
          return
        }
        if (!targetDir.exists()) {
          targetDir.mkdirs()
        }
        project.logger.info("Processing GMD in ${sourceDir} -> ${targetDir}, type: ${outputType}")

        List<ArtifactRepository> addedRepositories = []
        Configuration configuration = addDependencies(project, addedRepositories,
            groovyVersion, log4jVersion, gmdVersion, ivyVersion
        )
        // a configuration is a FileCollection, no need to call resolve()
        def result = execOperations.javaexec( a -> {
          a.classpath = configuration
          a.mainClass.set('se.alipsa.gmd.core.GmdProcessor')
          a.args = [
            sourceDir.canonicalPath,
            targetDir.canonicalPath,
            outputType
          ]
        })
        // cleanup the added repositories
        addedRepositories.each { repo ->
          project.repositories.remove(repo)
        }
        result.assertNormalExitValue()
        if (sourceDir.listFiles().size() > 0) {
          if (targetDir.exists()) {
            project.logger.quiet("Gmd files processed and written to ${targetDir.canonicalPath}")
          } else {
            project.logger.warn("${targetDir.canonicalPath} should exists but does not, something is probably wrong")
          }
        } else {
          project.logger.quiet("No gmd files found in ${sourceDir.canonicalPath}, nothing to do")
        }
      }
    }
    project.afterEvaluate {
      try {
        def runTaskBefore = extension.runTaskBefore.getOrElse('test')
        TaskProvider<Task> buildTask = it.tasks.named(runTaskBefore)
        buildTask.configure { Task task ->
          task.dependsOn(processGmdTask)
        }
      } catch (Exception e) {
        project.logger.warn("Could not add processGmd task before the test task: ${e.message}")
      }
    }
  }

  static Configuration addDependencies(Project project, List<ArtifactRepository> addedRepositories, String groovyVersion, String log4jVersion, String gmdVersion, String ivyVersion) {
    def mavenCentral = project.repositories.mavenCentral()
    if (!hasRepository(project, mavenCentral)) {
      project.repositories.add(mavenCentral)
      addedRepositories.add(mavenCentral)
    }
    return project.configurations.detachedConfiguration(
        project.dependencies.create("org.apache.groovy:groovy:${groovyVersion}"),
        project.dependencies.create("org.apache.groovy:groovy-templates:${groovyVersion}"),
        project.dependencies.create("org.apache.groovy:groovy-jsr223:${groovyVersion}"),
        project.dependencies.create("org.apache.ivy:ivy:${ivyVersion}"), // needed for @Grab)
        project.dependencies.create( "org.apache.logging.log4j:log4j-core:${log4jVersion}"),
        project.dependencies.create("se.alipsa.gmd:gmd-core:$gmdVersion")
    )
  }

  static boolean hasRepository(Project project, MavenArtifactRepository repo) {
    return project.repositories.find {
      it instanceof MavenArtifactRepository && it.url == repo.url
    } != null
  }
}
