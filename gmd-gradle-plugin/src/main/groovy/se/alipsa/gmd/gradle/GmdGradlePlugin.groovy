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
      it.inputs.dir(project.provider {
        project.file(extension.sourceDir.getOrElse('src/main/gmd'))
      })
      it.outputs.dir(project.provider {
        project.file(extension.targetDir.getOrElse('build/gmd'))
      })
      it.inputs.property('outputType', project.provider {
        extension.outputType.getOrElse('md')
      })
      it.doLast {
        File sourceDir= project.file(extension.sourceDir.getOrElse("src/main/gmd"))
        File targetDir= project.file(extension.targetDir.getOrElse("build/gmd"))
        String outputType= extension.outputType.getOrElse('md').trim().toLowerCase(Locale.ROOT)
        String groovyVersion = extension.groovyVersion.getOrElse('5.0.8')
        String log4jVersion = extension.log4jVersion.getOrElse('2.26.1')
        String gmdVersion = extension.gmdVersion.getOrElse('3.0.2')
        String ivyVersion = extension.ivyVersion.getOrElse('2.6.0')
        String javaFxVersion = extension.javaFxVersion.getOrElse('23.0.2')

        if (!['md', 'html', 'pdf'].contains(outputType)) {
          throw new IllegalArgumentException("Unknown output type ${outputType}, expected either md, html or pdf")
        }

        if (!sourceDir.exists()) {
          project.logger.warn("Source directory ${sourceDir.canonicalPath} does not exist, nothing to do")
          return
        }
        if (!targetDir.exists()) {
          if (!targetDir.mkdirs() && !targetDir.isDirectory()) {
            throw new IllegalArgumentException("Could not create target directory ${targetDir.canonicalPath}")
          }
        } else if (!targetDir.isDirectory()) {
          throw new IllegalArgumentException("Target path ${targetDir.canonicalPath} is a file, not a directory")
        }
        project.logger.info("Processing GMD in ${sourceDir} -> ${targetDir}, type: ${outputType}")
        cleanStaleGeneratedFiles(project, sourceDir, targetDir, outputType)

        List<ArtifactRepository> addedRepositories = []
        Configuration configuration = addDependencies(project, addedRepositories,
            groovyVersion, log4jVersion, gmdVersion, ivyVersion, javaFxVersion, outputType
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
        File[] sourceFiles = sourceDir.listFiles()
        if (sourceFiles != null && sourceFiles.size() > 0) {
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

  static Configuration addDependencies(Project project, List<ArtifactRepository> addedRepositories,
                                       String groovyVersion, String log4jVersion, String gmdVersion,
                                       String ivyVersion, String javaFxVersion) {
    return addDependencies(project, addedRepositories, groovyVersion, log4jVersion, gmdVersion,
        ivyVersion, javaFxVersion, 'md')
  }

  static Configuration addDependencies(Project project, List<ArtifactRepository> addedRepositories,
                                       String groovyVersion, String log4jVersion, String gmdVersion,
                                       String ivyVersion, String javaFxVersion, String outputType) {
    def mavenCentral = project.repositories.mavenCentral()
    if (!hasRepository(project, mavenCentral)) {
      project.repositories.add(mavenCentral)
      addedRepositories.add(mavenCentral)
    }

    List<Dependency> dependencies = [
        project.dependencies.create("org.apache.groovy:groovy:${groovyVersion}"),
        project.dependencies.create("org.apache.groovy:groovy-templates:${groovyVersion}"),
        project.dependencies.create("org.apache.groovy:groovy-jsr223:${groovyVersion}"),
        project.dependencies.create("org.apache.ivy:ivy:${ivyVersion}"), // needed for @Grab)
        project.dependencies.create("org.apache.logging.log4j:log4j-core:${log4jVersion}"),
        project.dependencies.create("se.alipsa.gmd:gmd-core:${gmdVersion}")
    ]

    // Only the styled PDF mode needs JavaFX. In particular, chart rendering
    // uses SVG and must not pull JavaFX into this detached configuration.
    if ('pdf'.equalsIgnoreCase(outputType)) {
      String platform = platformClassifier()
      dependencies.addAll([
          project.dependencies.create("org.openjfx:javafx-base:${javaFxVersion}:${platform}"),
          project.dependencies.create("org.openjfx:javafx-graphics:${javaFxVersion}:${platform}"),
          project.dependencies.create("org.openjfx:javafx-controls:${javaFxVersion}:${platform}"),
          project.dependencies.create("org.openjfx:javafx-swing:${javaFxVersion}:${platform}"),
          project.dependencies.create("org.openjfx:javafx-web:${javaFxVersion}:${platform}")
      ])
    }
    return project.configurations.detachedConfiguration(dependencies.toArray(new Dependency[0]))
  }

  private static String platformClassifier() {
    String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT)
    String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT)
    if (osName.contains("mac") || osName.contains("darwin")) {
      return osArch.contains("aarch64") || osArch.contains("arm") ? "mac-aarch64" : "mac"
    }
    if (osName.contains("linux")) {
      return "linux"
    }
    if (osName.contains("win")) {
      return "win"
    }
    throw new IllegalStateException("Unsupported OS: ${osName}")
  }

  private static void cleanStaleGeneratedFiles(Project project, File sourceDir, File targetDir, String outputType) {
    Set<String> expected = [] as Set
    File[] sources = sourceDir.listFiles({ File file -> file.isFile() && file.name.endsWith('.gmd') } as FileFilter)
    if (sources != null) {
      sources.each { file ->
        String base = file.name.substring(0, file.name.length() - 4)
        expected.add("${base}.${outputType}".toString())
      }
    }
    File[] generated = targetDir.listFiles({ File file ->
      file.isFile() && (file.name.endsWith('.md') || file.name.endsWith('.html') || file.name.endsWith('.pdf'))
    } as FileFilter)
    if (generated != null) {
      generated.findAll { !expected.contains(it.name) }.each { File file ->
        if (file.delete()) {
          project.logger.lifecycle("Removed stale generated GMD output ${file.absolutePath}")
        } else {
          project.logger.warn("Could not remove stale generated GMD output ${file.absolutePath}")
        }
      }
    }
  }

  static boolean hasRepository(Project project, MavenArtifactRepository repo) {
    return project.repositories.find {
      it instanceof MavenArtifactRepository && it.url == repo.url
    } != null
  }
}
