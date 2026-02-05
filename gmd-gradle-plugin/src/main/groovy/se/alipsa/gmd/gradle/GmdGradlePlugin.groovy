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
        File sourceDir= project.file(extension.sourceDir.getOrElse("src/main/gmd"))
        File targetDir= project.file(extension.targetDir.getOrElse("build/gmd"))
        String outputType= extension.outputType.getOrElse('md')
        String groovyVersion = extension.groovyVersion.getOrElse('5.0.4')
        String log4jVersion = extension.log4jVersion.getOrElse('2.25.3')
        String gmdVersion = extension.gmdVersion.getOrElse('3.0.2')
        String ivyVersion = extension.ivyVersion.getOrElse('2.5.3')
        String javaFxVersion = extension.javaFxVersion.getOrElse('23.0.2')

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
            groovyVersion, log4jVersion, gmdVersion, ivyVersion, javaFxVersion
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

  static Configuration addDependencies(Project project, List<ArtifactRepository> addedRepositories, String groovyVersion, String log4jVersion, String gmdVersion, String ivyVersion, String javaFxVersion) {
    def mavenCentral = project.repositories.mavenCentral()
    if (!hasRepository(project, mavenCentral)) {
      project.repositories.add(mavenCentral)
      addedRepositories.add(mavenCentral)
    }

    // Determine platform classifier for JavaFX
    String osName = System.getProperty("os.name").toLowerCase()
    String osArch = System.getProperty("os.arch").toLowerCase()
    String platform
    if (osName.contains("mac") || osName.contains("darwin")) {
      platform = osArch.contains("aarch64") || osArch.contains("arm") ? "mac-aarch64" : "mac"
    } else if (osName.contains("linux")) {
      platform = "linux"
    } else if (osName.contains("win")) {
      platform = "win"
    } else {
      throw new IllegalStateException("Unsupported OS: ${osName}")
    }

    return project.configurations.detachedConfiguration(
        project.dependencies.create("org.apache.groovy:groovy:${groovyVersion}"),
        project.dependencies.create("org.apache.groovy:groovy-templates:${groovyVersion}"),
        project.dependencies.create("org.apache.groovy:groovy-jsr223:${groovyVersion}"),
        project.dependencies.create("org.apache.ivy:ivy:${ivyVersion}"), // needed for @Grab)
        project.dependencies.create( "org.apache.logging.log4j:log4j-core:${log4jVersion}"),
        project.dependencies.create("se.alipsa.gmd:gmd-core:$gmdVersion"),
        // JavaFX modules - add all required modules with platform-specific classifiers
        project.dependencies.create("org.openjfx:javafx-base:${javaFxVersion}:${platform}"),
        project.dependencies.create("org.openjfx:javafx-graphics:${javaFxVersion}:${platform}"),
        project.dependencies.create("org.openjfx:javafx-controls:${javaFxVersion}:${platform}"),
        project.dependencies.create("org.openjfx:javafx-swing:${javaFxVersion}:${platform}"),
        project.dependencies.create("org.openjfx:javafx-web:${javaFxVersion}:${platform}")
    )
  }

  static boolean hasRepository(Project project, MavenArtifactRepository repo) {
    return project.repositories.find {
      it instanceof MavenArtifactRepository && it.url == repo.url
    } != null
  }
}
