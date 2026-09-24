package se.alipsa.gmd.gradle

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.internal.GradleInternal
import org.gradle.api.tasks.TaskProvider

@CompileStatic
class GmdGradlePlugin implements Plugin<Project> {

  private static final List<String> MAVEN_CENTRAL_HOSTS = ['repo.maven.apache.org', 'repo1.maven.org']
  private static final String GMD_PROCESSOR_RUNTIME_CONFIGURATION = 'gmdProcessorRuntime'

  @Override
  void apply(Project project) {
    GmdGradlePluginParams extension = project.extensions.create('gmdPlugin', GmdGradlePluginParams)
    extension.sourceDir.convention('src/main/gmd')
    extension.targetDir.convention('build/gmd')
    extension.outputType.convention('md')
    extension.groovyVersion.convention('5.1.3')
    extension.log4jVersion.convention('2.26.1')
    extension.gmdVersion.convention(project.providers.provider { ProcessGmdTask.defaultGmdVersion() })
    extension.ivyVersion.convention('2.6.0')
    extension.runTaskBefore.convention('test')

    TaskProvider<ProcessGmdTask> processGmdTask = project.tasks.register('processGmd', ProcessGmdTask)

    project.afterEvaluate {
      String sourceDir = extension.sourceDir.get()
      String targetDir = extension.targetDir.get()
      String outputType = extension.outputType.get()
      String groovyVersion = extension.groovyVersion.get()
      String log4jVersion = extension.log4jVersion.get()
      String gmdVersion = extension.gmdVersion.get()
      String ivyVersion = extension.ivyVersion.get()
      Configuration configuration = addDependencies(project,
          groovyVersion, log4jVersion, gmdVersion, ivyVersion
      )

      File resolvedTargetDir = project.file(targetDir)
      File buildDir = project.layout.buildDirectory.get().asFile
      // Only the conventional build/gmd directory is owned by this task.
      // Being somewhere under build/ does not establish ownership: build and
      // build/docs may contain outputs from unrelated tasks.
      boolean targetDirIsDefaultGmdOutput = resolvedTargetDir.canonicalFile == new File(buildDir, 'gmd').canonicalFile

      processGmdTask.configure { ProcessGmdTask task ->
        // Resolve all project values during configuration. The task action only
        // uses task properties and injected services, which enables the
        // configuration cache and parallel task execution.
        task.sourceDir.set(project.file(sourceDir))
        task.targetDir.set(resolvedTargetDir)
        task.outputType.set(outputType)
        File configuredSourceDir = project.file(sourceDir)
        task.runtimeClasspath.from(project.providers.provider {
          hasGmdFiles(configuredSourceDir) ? configuration : project.files()
        })
        task.targetDirIsDefaultGmdOutput.set(targetDirIsDefaultGmdOutput)
        if (targetDirIsDefaultGmdOutput) {
          task.dedicatedOutputDir.set(resolvedTargetDir)
        }
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
    if (!isRepositoriesModeSettingsManaged(project) && !hasMavenCentral(project)) {
      try {
        project.repositories.mavenCentral()
      } catch (InvalidUserCodeException e) {
        project.logger.info('Project repositories are managed in settings.gradle; ' +
            'resolving gmd-core through the settings repositories instead: ' + e.message)
      }
    }

    List<Dependency> dependencies = [
        project.dependencies.create("org.apache.groovy:groovy:${groovyVersion}"),
        project.dependencies.create("org.apache.groovy:groovy-templates:${groovyVersion}"),
        project.dependencies.create("org.apache.groovy:groovy-jsr223:${groovyVersion}"),
        project.dependencies.create("org.apache.ivy:ivy:${ivyVersion}"), // needed for @Grab)
        project.dependencies.create("org.apache.logging.log4j:log4j-core:${log4jVersion}"),
        project.dependencies.create("se.alipsa.gmd:gmd-core:${gmdVersion}")
    ]

    Configuration configuration = project.configurations.maybeCreate(GMD_PROCESSOR_RUNTIME_CONFIGURATION)
    configuration.canBeConsumed = false
    configuration.canBeResolved = true
    configuration.visible = false
    configuration.dependencies.addAll(dependencies)
    return configuration
  }

  static boolean hasMavenCentral(Project project) {
    return project.repositories.any { ArtifactRepository repository ->
      repository instanceof MavenArtifactRepository &&
          MAVEN_CENTRAL_HOSTS.contains(((MavenArtifactRepository) repository).url?.host)
    }
  }

  private static boolean hasGmdFiles(File directory) {
    File[] files = directory.listFiles({ File file ->
      file.isFile() && file.name.endsWith('.gmd')
    } as FileFilter)
    return files != null && files.length > 0
  }

  /**
   * Under {@code PREFER_SETTINGS}, Gradle does not reject a project-declared repository
   * with {@link InvalidUserCodeException} the way it does under {@code FAIL_ON_PROJECT_REPOS};
   * it silently accepts (and deprecates) it instead. That leaves the {@code hasMavenCentral}
   * check blind - project.repositories still reports zero entries beforehand - so without this
   * check the plugin would add, and permanently mutate every consumer's project.repositories
   * with, a repository that dependency resolution ignores anyway. There is no public API for a
   * project plugin to read the resolved repositories mode, so this reaches into Gradle's
   * internal API (bundled by gradleApi(), verified on Gradle 9.7.1). If that internal API is
   * ever unavailable, this falls back to false and the InvalidUserCodeException guard in
   * addDependencies still handles FAIL_ON_PROJECT_REPOS as before.
   */
  private static boolean isRepositoriesModeSettingsManaged(Project project) {
    try {
      GradleInternal gradleInternal = (GradleInternal) project.gradle
      RepositoriesMode mode = gradleInternal.settings.dependencyResolutionManagement.repositoriesMode
          .getOrElse(RepositoriesMode.PREFER_PROJECT)
      return mode != RepositoriesMode.PREFER_PROJECT
    } catch (Throwable ignored) {
      return false
    }
  }
}
