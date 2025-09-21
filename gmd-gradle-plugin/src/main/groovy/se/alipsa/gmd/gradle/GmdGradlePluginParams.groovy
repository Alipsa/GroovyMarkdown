package se.alipsa.gmd.gradle

import groovy.transform.CompileStatic
import org.gradle.api.provider.Property

@CompileStatic
interface GmdGradlePluginParams {
  /**
   * The directory where the GMD files are located. Default is src/main/gmd
   * The path is relative to the project directory.
   */
  Property<String> getSourceDir()

  /**
   * The directory where the generated files will be written. Default is build/gmd
   * The path is relative to the project directory.
   */
  Property<String> getTargetDir()

  /**
   * The type of output to generate. Can be one of:
   * - md
   * - html
   * - pdf
   */
  Property<String> getOutputType()

  /**
   * The version of Groovy to use. Default is 5.0.1
   */
  Property<String> getGroovyVersion()

  /**
   * The version of Log4j to use. Default is 2.24.3
   */
  Property<String> getLog4jVersion()

  /**
   * The version of GMD to use. Default is 3.0.0
   */
  Property<String> getGmdVersion()

  /**
   * The version of Ivy to use. Default is 2.5.3
   */
  Property<String> getIvyVersion()

  /**
   * The task that the gmd plugin should run before. Default is 'test'
   * @return
   */
  Property<String> getRunTaskBefore()
}
