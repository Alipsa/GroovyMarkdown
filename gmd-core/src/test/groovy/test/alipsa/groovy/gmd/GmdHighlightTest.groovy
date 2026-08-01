package test.alipsa.groovy.gmd

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import se.alipsa.gmd.core.Gmd

class GmdHighlightTest extends AbstractGmdTest {

  @Test
  void testHighlight() {
    def text = """
      # Test
      
      ```{groovy echo=false}
      def a = 3
      for (i in 1..a) {
        out.println('Hello ' + i)  
      }
      ```
      
      - first 
      - second
      
      ```groovy
      def q = 213
      println('q is ' + q)
      ```
       
      X = ∑(√2π + ∛3) = `=Math.sqrt(2* Math.PI) + Math.cbrt(3)`
      """.stripIndent()
    def gmd = new Gmd()
    def html = gmd.gmdToHtmlDoc(text)

    // create a pdf file from the html
    def pdfFile = new File(AbstractGmdTest.testOutputDir, "testHighlight.pdf")
    if (pdfFile.exists()) pdfFile.delete()
    gmd.processHtmlAndSaveAsPdf(html, pdfFile)
    //gmd.gmdToPdf(text, pdfFile)
    Assertions.assertTrue(pdfFile.exists(), "Failed to create pdf file")
  }
}
