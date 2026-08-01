package test.alipsa.groovy.gmd

import org.apache.commons.io.IOUtils

import java.nio.charset.StandardCharsets
import java.util.Base64

import static org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import se.alipsa.gmd.core.Gmd

class GmdTest extends AbstractGmdTest {

  def text = """\
        # Hello
        ```{groovy echo=false}
        import java.time.LocalDate
        import java.time.format.TextStyle
        import java.util.Locale
        
        // note that we MUST not use def here but define the variable globally
        now = LocalDate.parse("2022-07-23")
        
        def dayName(theDate) {
          return theDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
        ```
        
        Today (`= dayName(now)`) is `= now `.
        
        The weather in next 3 days will be:
        ```{groovy echo=false}
          def weather = [ "Sunny", "Rainy", "Cloudy", "Windy" ]
          for (i = 1; i < 4; i++) {
            def day = now.plusDays(i)
            out.println "- " + dayName(day) + ": " + weather.get(i-1)
          }
        ```
        
        Now, that's something to look forward to!
        
        """.stripIndent()

  @Test
  void gmdToHtmlFile() {
    def gmd = new Gmd()
    def htmlfFile = new File(AbstractGmdTest.testOutputDir, "gmdToHtmlFile.pdf")
    if (htmlfFile.exists()) htmlfFile.delete()
    gmd.gmdToHtml(text, htmlfFile)
    assertEquals(gmd.gmdToHtmlDoc(text), htmlfFile.text)

    StringWriter sw = new StringWriter()
    gmd.gmdToHtml(text, sw)
    assertEquals(htmlfFile.text, sw.toString())
  }

  @Test
  void gmdToPdf() {
    def gmd = new Gmd()
    def pdfFile = new File(AbstractGmdTest.testOutputDir, "gmdToPdf.pdf")
    if (pdfFile.exists()) pdfFile.delete()
    def html = gmd.gmdToHtmlDoc(text)
    gmd.htmlToPdf(html, pdfFile)
    assertTrue(pdfFile.exists())
    def pdfFile2 = new File(AbstractGmdTest.testOutputDir, "gmdToPdf2.pdf")
    if (pdfFile2.exists()) pdfFile2.delete()
    gmd.gmdToPdf(text, pdfFile2)
    assertTrue(pdfFile.exists())
    // Files might differ with a few bytes
    assertEquals((pdfFile.length()/15).intValue(), (pdfFile2.length()/15).intValue())

    def pdfFile3 = new File(AbstractGmdTest.testOutputDir, "gmdToPdf3.pdf")
    gmd.gmdToPdf(text, pdfFile3)
    assertEquals((pdfFile2.length()/15).intValue(), (pdfFile3.length()/15).intValue())
  }

  @Test
  void gmdToMd() {
    def gmd = new Gmd()
    def md = gmd.gmdToMd(text)
    assertEquals("""\
        # Hello

        Today (Saturday) is 2022-07-23.
        
        The weather in next 3 days will be:
        - Sunday: Sunny
        - Monday: Rainy
        - Tuesday: Cloudy
        
        Now, that's something to look forward to!
        """.stripIndent(), md)
  }

  @Test
  void gmdToHtml() {
    def gmd = new Gmd()
    def html = gmd.gmdToHtml(text)
    assertEquals("""\
            <h1>Hello</h1>
            <p>Today (Saturday) is 2022-07-23.</p>
            <p>The weather in next 3 days will be:</p>
            <ul>
            <li>Sunday: Sunny</li>
            <li>Monday: Rainy</li>
            <li>Tuesday: Cloudy</li>
            </ul>
            <p>Now, that's something to look forward to!</p>
            """.stripIndent(), html)
  }

  @Test
  void testInlineExpression() {
    def gmd = new Gmd()
    def html = gmd.gmdToHtml('1 + 2 = `= 1+2`')
    assertEquals('<p>1 + 2 = 3</p>\n', html)
  }

  @Test
  void testParameterized() {
    def gmd = new Gmd();
    def html = gmd.gmdToHtml('Today is `= theDate`', [theDate: '2023-08-14'])
    assertEquals('<p>Today is 2023-08-14</p>\n', html)
  }

  @Test
  void gmdToMdWithParameter() {
    def text = '## Hello `=name`!'
    def gmd = new Gmd()
    def md = gmd.gmdToMd(text, [name: "Per"])
    assertEquals("## Hello Per!", md)
  }

  @Test
  void gmdToHtmlWithParameter() {
    def text = '## Hello `=name`!'
    def gmd = new Gmd()
    def html = gmd.gmdToHtml(text, [name: "Per"])
    assertEquals("<h2>Hello Per!</h2>\n", html)
  }

  @Test
  void gmdToPdfWithParameter() {
    def text = '## Hello `=name`!'
    def gmd = new Gmd()
    def html = gmd.gmdToHtmlDoc(text, [name: "Per"])
    def pdfFile = new File(AbstractGmdTest.testOutputDir, "gmdToPdfWithParameter.pdf")
    if (pdfFile.exists()) pdfFile.delete()
    gmd.htmlToPdf(html, pdfFile)
    assertTrue(pdfFile.exists())
  }

  @Test
  void testHtmlWithSpecialCharacters() {
    String text = """\
        # Some equations
        X = &sum;(&radic;2&pi; + &#8731;3)
        """.stripIndent()

    def gmd = new Gmd()
    String html = gmd.gmdToHtml(text)

    assertEquals("<h1>Some equations</h1>\n" +
        "<p>X = ∑(√2π + ∛3)</p>\n", html)
  }

  @Test
  void testPdfWithSpecialCharacters() {
    String text = """\
        # Some equations
        X = ∑(√2π + ∛3)
        """.stripIndent()

    def gmd = new Gmd()
    String html = gmd.gmdToHtmlDoc(text)

    assertTrue(html.contains("<h1>Some equations</h1>\n<p>X = ∑(√2π + ∛3)</p>\n"))

    def pdfFile = new File(AbstractGmdTest.testOutputDir, "testPdfWithSpecialCharacters.pdf")
    if (pdfFile.exists()) pdfFile.delete()
    gmd.htmlToPdf(html, pdfFile)
    assertTrue(pdfFile.exists())
  }

  @Test
  void testMatrixTable() {
    def text = '''
    # Applications on `=the_date`
    ```{groovy echo=false}
    // @Grab('se.alipsa.matrix:matrix-core:2.2.0')
    
    import static se.alipsa.matrix.core.ListConverter.*
      
    import se.alipsa.matrix.core.Matrix
    import java.time.LocalDate
    out.print(Matrix.builder().data(
        emp_id: 1..5,
        emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
        salary: [623.3,515.2,611.0,729.0,843.25],
        start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"))
      .types(int, String, Number, LocalDate).build()
    )
    ```
    '''.stripIndent()

    Gmd gmd = new Gmd();

    def html = gmd.gmdToHtml(text, [the_date: '2023-08-16'])
    assertEquals('''      <h1>Applications on 2023-08-16</h1>
      <table class="table">
        <thead>
          <tr>
            <th class='emp_id Integer' style='text-align: right'>emp_id</th>
            <th class='emp_name String'>emp_name</th>
            <th class='salary Number' style='text-align: right'>salary</th>
            <th class='start_date LocalDate'>start_date</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td class='emp_id Integer' style='text-align: right'>1</td>
            <td class='emp_name String'>Rick</td>
            <td class='salary Number' style='text-align: right'>623.3</td>
            <td class='start_date LocalDate'>2012-01-01</td>
          </tr>
          <tr>
            <td class='emp_id Integer' style='text-align: right'>2</td>
            <td class='emp_name String'>Dan</td>
            <td class='salary Number' style='text-align: right'>515.2</td>
            <td class='start_date LocalDate'>2013-09-23</td>
          </tr>
          <tr>
            <td class='emp_id Integer' style='text-align: right'>3</td>
            <td class='emp_name String'>Michelle</td>
            <td class='salary Number' style='text-align: right'>611.0</td>
            <td class='start_date LocalDate'>2014-11-15</td>
          </tr>
          <tr>
            <td class='emp_id Integer' style='text-align: right'>4</td>
            <td class='emp_name String'>Ryan</td>
            <td class='salary Number' style='text-align: right'>729.0</td>
            <td class='start_date LocalDate'>2014-05-11</td>
          </tr>
          <tr>
            <td class='emp_id Integer' style='text-align: right'>5</td>
            <td class='emp_name String'>Gary</td>
            <td class='salary Number' style='text-align: right'>843.25</td>
            <td class='start_date LocalDate'>2015-03-27</td>
          </tr>
        </tbody>
      </table>
        '''.stripIndent(), html)

    text = '''
    ```{groovy}
    import static se.alipsa.matrix.core.ListConverter.*

    import se.alipsa.matrix.core.*
    import se.alipsa.matrix.pict.*
    import java.time.LocalDate

    def empData = Matrix.builder().data(
        emp_id: 1..5,
        emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
        salary: [623.3,515.2,611.0,729.0,843.25],
        start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"))
        .types([int, String, Number, LocalDate])
        .build()
    BarChart chart = BarChart.createVertical("Salaries", empData, "emp_name", ChartType.BASIC, "salary")
    out.println(chart)
    ```
    '''
    html = gmd.gmdToHtml(text)
    assertTrue(html.contains('BarChart chart = BarChart.createVertical('), 'Should contain code content')
    assertTrue(html.contains('<p><img src="data:image/svg+xml;base64,'), 'Should contain SVG image content')
  }

  @Test
  void gmdToHtmlDoc() {
    String text = """\
      # Test
      ```{groovy echo=false}
      def a = 3
      for (i in 1..a) {
        out.println("Hello \${i}")  
      }
      ```
      
      - first 
      - second
      
      ```groovy
      def q = 213
      println('q is ' + q)
      ``` 
      X = &sum;(&radic;2&pi; + &#8731;3)
      X = ∑(√2π + ∛3)  
    """.stripIndent()
    def gmd = new Gmd()

    def html = gmd.gmdToHtmlDoc(text)
    assertTrue (html.contains("""\
      <h1>Test</h1>
      <p>Hello 1<br />
      Hello 2<br />
      Hello 3</p>
      <ul>
      <li>first</li>
      <li>second</li>
      </ul>
      <pre><code class="language-groovy">def q = 213
      println('q is ' + q)
      </code></pre>
      <p>X = ∑(√2π + ∛3)<br />
      X = ∑(√2π + ∛3)</p>
      
      </body>""".stripIndent()), html)
    assertTrue(html.startsWith("""\
      <!DOCTYPE html PUBLIC
      "-//OPENHTMLTOPDF//MATH XHTML Character Entities With MathML 1.0//EN" "">
      <html>""".stripIndent()), "Doctype declaration is missing\n" + html)
    assertTrue(html.contains("code.hljs{"), "Highligtjs style missing:\n" + html)
    assertTrue(html.contains("bs-blue:"), "Bootrap style missing:\n" + html)
    assertTrue(html.contains("hljs=function()"), "highlighJs init script missing")
  }

  @Test
  void traditionalMarkdownCode() {
    def text = """
    # The thing
    Here it is
    ```{groovy}
      import java.time.LocalDate
      import java.time.format.TextStyle
      import java.util.Locale

      def now = LocalDate.parse("2022-07-23")
      def dayName(theDate) {
        return theDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())
      }
      out.println "Today (" + dayName(now) + ") is " + now + "."
    ```
    How about that?    
    """.stripIndent()
    def gmd = new Gmd()
    def md = gmd.gmdToMd(text)
    
    assertEquals("""
    # The thing
    Here it is
    ```groovy
      import java.time.LocalDate
      import java.time.format.TextStyle
      import java.util.Locale
    
      def now = LocalDate.parse("2022-07-23")
      def dayName(theDate) {
        return theDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())
      }
      out.println "Today (" + dayName(now) + ") is " + now + "."
    ```
    Today (Saturday) is 2022-07-23.
    How about that?""".stripIndent(), md)
  }

  @Test
  void testInlineVars() {
    def gmd = new Gmd()
    text = """
        ```{groovy echo=false}
        x = 5
        ```
        X = `= x`
        
        """.stripIndent()

    assertEquals("""
        X = 5
        """.stripIndent(), gmd.gmdToMd(text))

    assertEquals("""<p>X = 5</p>
        """.stripIndent(), gmd.gmdToHtml(text))
  }

  @Test
  void testChart() {
    def text = '''
# Employees

```{groovy}
import static se.alipsa.matrix.core.ListConverter.*

import se.alipsa.matrix.core.*
import se.alipsa.matrix.pict.*
import java.time.LocalDate 

def empData = Matrix.builder().data(
            emp_id: 1..5,
            emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
            salary: [623.3,515.2,611.0,729.0,843.25],
            start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"))
            .types(int, String, Number, LocalDate)
            .build()
BarChart chart = BarChart.createVertical("Salaries", empData, "emp_name", ChartType.BASIC, "salary")
out.println(chart)
```
'''
    Gmd gmd = new Gmd()
    String md = gmd.gmdToMd(text)
    assertTrue(md.contains('# Employees'))
    String image = md.split("data:image/svg\\+xml;base64,", 2)[1].split("\\)", 2)[0]
    assertTrue(new String(Base64.decoder.decode(image), StandardCharsets.UTF_8).contains('<svg'), 'Chart should be SVG-backed')
  }

  @Test
  void testXChart() {
    def text = '''
# Employees

```{groovy echo=false}
import static se.alipsa.matrix.core.ListConverter.*

import se.alipsa.matrix.core.*
import se.alipsa.matrix.xchart.*
import java.time.LocalDate 

import static se.alipsa.matrix.core.ListConverter.*

def empData = Matrix.builder().data(
            emp_id: 1..5,
            emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
            salary: [623.3,515.2,611.0,729.0,843.25],
            start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"))
            .types(int, String, Number, LocalDate)
            .build()
BarChart chart = BarChart.create(empData, 800, 600)
        .setTitle("Salaries")
        .addSeries("Salaries", "emp_name", "salary")
out.println(chart)
```
'''
    Gmd gmd = new Gmd()
    String md = gmd.gmdToMd(text)
    assertTrue(md.contains('# Employees'))
    assertTrue(md.contains("![''](data:image/svg+xml;base64,"))

    def htmlFile = new File(AbstractGmdTest.testOutputDir, "testXChart.html")
    gmd.gmdToHtml(text, htmlFile)
    assertTrue(htmlFile.exists())
    assertTrue(htmlFile.length() > 100, "No html content")
    assertTrue(htmlFile.text.contains('<h1>Employees</h1>'))
    assertTrue(htmlFile.text.contains('<img src="data:image/svg+xml;base64,'), "No SVG image content")
  }

  @Test
  void testMathmlToPDF() {
    def html = IOUtils.toString(this.class.getResource('/mathml.html'), StandardCharsets.UTF_8)
    Gmd gmd = new Gmd()
    def pdfFile = new File(AbstractGmdTest.testOutputDir, "testMathmlToPDF.pdf")
    gmd.htmlToPdf(html, pdfFile)
    assertTrue(pdfFile.exists())
    println("Wrote $pdfFile.absolutePath")
  }

  @Test
  void gmdCommandLineTest() {
    String file = getClass().getResource("/test.gmd").getFile()

    File htmlFile = new File(AbstractGmdTest.testOutputDir,"gmdCommandLineTest.html")
    Gmd.main("toHtml", file, htmlFile.absolutePath)
    assertTrue(htmlFile.exists())
    assertTrue(htmlFile.length() > 100, "No html content")

    File pdfFile = new File(AbstractGmdTest.testOutputDir, "gmdCommandLineTest.pdf")
    Gmd.main("toPdf", file, pdfFile.absolutePath)
    assertTrue(pdfFile.exists())
    assertTrue(pdfFile.length() > 100, "No pdf content")
  }

  @Test
  void testEnsureResultDoesNotPrint() {
    String text = """
    ```{groovy}
    a = 23
    ```
    12 + a = `= 12 + a`
    """.stripIndent()
    Gmd gmd = new Gmd()
    assertEquals('''
    ```groovy
    a = 23
    ```
    12 + a = 35'''.stripIndent(), gmd.gmdToMd(text))
  }
}
