package test.alipsa.groovy.gmd

import org.junit.jupiter.api.BeforeAll

class AbstractGmdTest {

  static File testOutputDir = new File("build/test-results/")

  @BeforeAll
  static void init() {
    if (testOutputDir.exists()) {
      testOutputDir.deleteDir()
    }
    if (testOutputDir.exists() || (!testOutputDir.mkdirs() && !testOutputDir.isDirectory())) {
      throw new IllegalStateException("Could not recreate test output directory ${testOutputDir.absolutePath}")
    }
  }
}
