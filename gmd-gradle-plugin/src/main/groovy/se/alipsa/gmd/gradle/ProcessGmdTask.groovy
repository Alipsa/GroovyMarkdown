package se.alipsa.gmd.gradle

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.JavaExecSpec
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

@CompileStatic
@DisableCachingByDefault(because = 'GMD processing may generate non-deterministic PDF metadata')
abstract class ProcessGmdTask extends DefaultTask {

  private final ExecOperations execOperations

  @Inject
  ProcessGmdTask(ExecOperations execOperations) {
    this.execOperations = execOperations
  }

  @InputDirectory
  @Optional
  @PathSensitive(PathSensitivity.RELATIVE)
  abstract DirectoryProperty getSourceDir()

  @OutputDirectory
  abstract DirectoryProperty getTargetDir()

  @Input
  abstract org.gradle.api.provider.Property<String> getOutputType()

  @org.gradle.api.tasks.Classpath
  abstract ConfigurableFileCollection getClasspath()

  @TaskAction
  void process() {
    File source = getSourceDir().get().asFile
    File target = getTargetDir().get().asFile
    String output = getOutputType().get().trim().toLowerCase(Locale.ROOT)
    if (!['md', 'html', 'pdf'].contains(output)) {
      throw new IllegalArgumentException("Unknown output type ${output}, expected either md, html or pdf")
    }

    if (!source.exists()) {
      logger.warn("Source directory ${source.canonicalPath} does not exist, nothing to do")
      return
    }
    if (!target.exists()) {
      if (!target.mkdirs() && !target.isDirectory()) {
        throw new IllegalArgumentException("Could not create target directory ${target.canonicalPath}")
      }
    } else if (!target.isDirectory()) {
      throw new IllegalArgumentException("Target path ${target.canonicalPath} is a file, not a directory")
    }
    logger.info("Processing GMD in ${source} -> ${target}, type: ${output}")
    cleanStaleGeneratedFiles(source, target, output)

    def result = execOperations.javaexec { JavaExecSpec spec ->
      spec.classpath = getClasspath()
      spec.mainClass.set('se.alipsa.gmd.core.GmdProcessor')
      spec.args = [source.canonicalPath, target.canonicalPath, output]
    }
    result.assertNormalExitValue()
    File[] sourceFiles = source.listFiles()
    if (sourceFiles != null && sourceFiles.size() > 0) {
      if (target.exists()) {
        logger.quiet("Gmd files processed and written to ${target.canonicalPath}")
      } else {
        logger.warn("${target.canonicalPath} should exists but does not, something is probably wrong")
      }
    } else {
      logger.quiet("No gmd files found in ${source.canonicalPath}, nothing to do")
    }
  }

  private void cleanStaleGeneratedFiles(File sourceDir, File targetDir, String outputType) {
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
          logger.lifecycle("Removed stale generated GMD output ${file.absolutePath}")
        } else {
          logger.warn("Could not remove stale generated GMD output ${file.absolutePath}")
        }
      }
    }
  }
}
