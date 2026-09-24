package se.alipsa.gmd.gradle

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFiles
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
    // Fail-safe default for anyone registering this task directly instead of
    // going through GmdGradlePlugin's afterEvaluate wiring: skip cleanup
    // rather than fail validation or delete files in a directory it does not own.
    targetDirIsDefaultGmdOutput.convention(false)
  }

  /**
   * The gmd-core version matching this plugin, from the generated
   * gmd-version.properties resource. Fails clearly when the resource is
   * missing or unexpanded.
   */
  @PackageScope
  static String defaultGmdVersion() {
    InputStream stream = ProcessGmdTask.class.getResourceAsStream('/gmd-version.properties')
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

  @InputDirectory
  @Optional
  @PathSensitive(PathSensitivity.RELATIVE)
  abstract DirectoryProperty getSourceDir()

  @org.gradle.api.tasks.Internal
  abstract DirectoryProperty getTargetDir()

  /**
   * The default build/gmd directory is dedicated to this task, so Gradle may
   * track the directory as a whole and remove obsolete output safely.
   */
  @Optional
  @OutputDirectory
  abstract DirectoryProperty getDedicatedOutputDir()

  /**
   * A custom target can be shared. Track only the files this task is expected
   * to generate so Gradle retains its up-to-date checks without owning the
   * whole directory or removing unrelated files.
   */
  @OutputFiles
  Set<File> getGeneratedFiles() {
    if (getTargetDirIsDefaultGmdOutput().getOrElse(false)
        || !getSourceDir().isPresent() || !getTargetDir().isPresent() || !getOutputType().isPresent()) {
      return [] as Set<File>
    }
    File source = getSourceDir().get().asFile
    File target = getTargetDir().get().asFile
    String output = getOutputType().get().trim().toLowerCase(Locale.ROOT)
    File[] sources = gmdFilesIn(source)
    Set<File> generated = sources.collect { File file ->
      new File(target, file.name.substring(0, file.name.length() - 4) + ".${output}")
    } as Set<File>
    return generated
  }

  @Input
  abstract org.gradle.api.provider.Property<String> getOutputType()

  /**
   * The actual runtime classpath used to fork GmdProcessor. When there is no
   * .gmd source, its provider supplies an empty collection so Gradle can run the
   * no-op and stale-output cleanup paths without resolving processor dependencies.
   */
  @Classpath
  abstract ConfigurableFileCollection getRuntimeClasspath()

  /**
   * @deprecated Use {@link #getRuntimeClasspath()}. This alias returns the
   * same collection for compatibility with directly registered tasks.
   */
  @Deprecated
  @org.gradle.api.tasks.Internal
  ConfigurableFileCollection getClasspath() {
    return getRuntimeClasspath()
  }

  @Input
  abstract org.gradle.api.provider.Property<Boolean> getTargetDirIsDefaultGmdOutput()

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
    File[] sourceFiles = gmdFilesIn(source)
    if (sourceFiles.length == 0) {
      if (getTargetDirIsDefaultGmdOutput().getOrElse(false) && target.isDirectory()) {
        cleanStaleGeneratedFiles(source, target, output)
      }
      logger.quiet("No gmd files found in ${source.canonicalPath}, nothing to do")
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
    if (getTargetDirIsDefaultGmdOutput().getOrElse(false)) {
      cleanStaleGeneratedFiles(source, target, output)
    } else {
      logger.info("Skipping stale generated-file cleanup for targetDir ${target.canonicalPath}; " +
          "only the dedicated default build/gmd directory is cleaned automatically")
    }

    def result = execOperations.javaexec { JavaExecSpec spec ->
      spec.classpath = getRuntimeClasspath()
      spec.mainClass.set('se.alipsa.gmd.core.GmdProcessor')
      spec.args = [source.canonicalPath, target.canonicalPath, output]
    }
    result.assertNormalExitValue()
    if (target.exists()) {
      logger.quiet("Gmd files processed and written to ${target.canonicalPath}")
    } else {
      logger.warn("${target.canonicalPath} should exists but does not, something is probably wrong")
    }
  }

  private void cleanStaleGeneratedFiles(File sourceDir, File targetDir, String outputType) {
    Set<String> expected = [] as Set
    gmdFilesIn(sourceDir).each { file ->
      String base = file.name.substring(0, file.name.length() - 4)
      expected.add("${base}.${outputType}".toString())
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

  /** The .gmd files in a directory, or an empty array when it cannot be read. */
  private static File[] gmdFilesIn(File directory) {
    File[] files = directory.listFiles({ File file ->
      file.isFile() && file.name.endsWith('.gmd')
    } as FileFilter)
    return files == null ? new File[0] : files
  }
}
