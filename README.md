[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.alipsa.gmd/gmd-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.alipsa.gmd/gmd-core)
[![javadoc](https://javadoc.io/badge2/se.alipsa.gmd/gmd-core/javadoc.svg)](https://javadoc.io/doc/se.alipsa.gmd/gmd-core)
# gmd - Groovy Markdown

Groovy markdown is basically markdown with some groovy code for dynamic rendering.
It is based on the GmdTemplateEngine and the [Commonmark
Markdown package](https://commonmark.org/).

A gmd file (or text) is markdown with groovy code in codeblocks starting with \```{groovy} and ending with \```
(similar to rmd and mdr files) and \`= \` for direct value output.

Here is an example:

```markdown
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
      out.println "## Today (" + dayName(now) + ") is " + now + "."
    ```
    How about that?    
```
This will generate the following markdown
````markdown
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
  out.println "## Today (" + dayName(now) + ") is " + now + "."
```
## Today (Saturday) is 2022-07-23.

How about that?
````
If you don't want to echo the code in the Markdown document you can set the
echo property to false e.g. \```{groovy echo=false}

Inline variables (similar to the <%= expression %> syntax in scriptlets) can be done using \`= expression \`
here is an example:
````markdown
```{groovy echo=false}
    aVal = 123 + 234
```
123 + 234 = `= aVal `
````
Which will result in
```markdown
123 + 234 = 357
```

This kind of Markdown text can then be transformed to html and pdf using the Gmd class e.g:
```groovy
def gmd = new se.alipsa.gmd.core.Gmd()
// html is a string of html markup
def html = gmd.gmdToHtml(text)

// create a pdf file from the html
def pdfFile = File.createTempFile("weather", ".pdf")
gmd.htmlToPdf(html, pdfFile)
```

If you want to pass parameters to be used in the gmd text/file you can do that like this:
```groovy
def text = 'Hello `=name`!'
def gmd = new se.alipsa.gmd.core.Gmd()
def md = gmd.gmdToMd(text, [name: "Per"])

// Or directly to html
def html = gmd.gmdToHtml(text, [name: "Per"])

// the html can then be used to create a pdf
gmd.htmlToPdf(html, [name: "Per"], new File("pdfFile.pdf"))
```

GMD supports the [Matrix](https://github.com/Alipsa/matrix) library directly, i.e. Matrix, Chart and MatrixXChart 
types can be used with the `out` PrintWriter object without needing to convert them into markdown first. 
Here is an example:
````
# Employees
    
```{groovy echo=false}
import static se.alipsa.matrix.core.ListConverter.*

import se.alipsa.matrix.core.*
import se.alipsa.matrix.xchart.*
import java.time.LocalDate 

import static se.alipsa.matrix.core.ListConverter.*

// if we dont do def or specify the type, the variable will be "global" and can be 
// used in subsequent code blocks
empData = Matrix.builder().data(
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
## Employee details
```{groovy echo=false}
out.println(empData)
```

````
For "Special" characters e.g. match symbol, you could use the html escape codes. E.g.
to write `X = ∑(√2π + ∛3)`, you could do `X = &amp;sum;(&amp;radic;2&amp;pi; + &amp;#8731;3)` and scope the 
expression with parenthesis as appropriate. Otherwise, it will show up as `X = ?(?2? + ?3)` when you turn it into html or pdf.
See [HTML Math Symbols](https://www.toptal.com/designers/htmlarrows/math/) for an extensive list.
An alternative is to generate a whole html doc encoded in UTF-8 that includes unicode fonts. 
The gmdToHtmlDoc() and mdToHtmlDoc() does just that. Those methods also includes highlightJs and Bootstrap in the html.

HighlightJS requires the execution of the highligtJs init script for the code sections to be properly formatted. 
In order for this to happen, the html code need to be rendered in a browser with javascript support. 

Gmd supports processing the javascript by running it in the JavaFx WebView as in the following 
example usage:

```groovy
def text = """
# Test

&grave;&grave;&grave;{groovy echo=false}
def a = 3
for (i in 1..a) {
  out.println('Hello ' + i)  
}
&grave;&grave;&grave;

- first 
- second

&grave;&grave;&grave;groovy
def q = 213
println('q is ' + q)
&grave;&grave;&grave;
 
X = ∑(√2π + ∛3) = `=Math.sqrt(2* Math.PI) + Math.cbrt(3)`
"""
def gmd = new Gmd()
def html = gmd.gmdToHtmlDoc(text)

// create a pdf file from the html
def pdfFile = File.createTempFile("test", ".pdf")
gmd.processHtmlAndSaveAsPdf(html, pdfFile)
```
Alternatives to using JavaFx WebView might be [Web-K](https://github.com/Earnix/Web-K) or [J2V8](https://github.com/eclipsesource/J2V8)
, but I have not tested any of those.

To use it from within a JavaFx application see the [GmdTestGui](https://github.com/Alipsa/gmd/tree/main/GmdTestGui/src/main/groovy/se/alipsa/gmdtest/GmdTestGui.groovy) 
for an approach that I found to provide the best performance and usability.

The library, which requires Java 21 or later, is available from maven central:

Gradle: 
```groovy
def groovyVersion = '5.0.1' // any 4.x version should work
implementation "se.alipsa.gmd:gmd-core:3.0.1"
implementation "org.apache.groovy:groovy:${groovyVersion}"
implementation "org.apache.groovy:groovy-templates:${groovyVersion}"
implementation "org.apache.groovy:groovy-jsr223:${groovyVersion}"
implementation 'org.apache.ivy:ivy:2.5.3'
```

Maven:
```xml
<build>
  <dependencies>  
    <dependency>
      <groupId>se.alipsa.gmd</groupId>
      <artifactId>gmd-core</artifactId>
      <version>3.0.0</version>
    </dependency>
    <dependency>
      <groupId>org.apache.groovy</groupId>
      <artifactId>groovy</artifactId>
      <version>5.0.1</version>
    </dependency>
    <dependency>
      <groupId>org.apache.groovy</groupId>
      <artifactId>groovy-templates</artifactId>
      <version>5.0.1</version>
    </dependency>
    <dependency>
      <groupId>org.apache.groovy</groupId>
      <artifactId>groovy-jsr223</artifactId>
      <version>5.0.1</version>
    </dependency>
    <dependency>
      <groupId>org.apache.ivy</groupId>
      <artifactId>ivy</artifactId>
      <version>2.5.3</version>
    </dependency>
  </dependencies>
</build>
```
## Using Gmd from the command line
The release artifacts on github contains a fat jar (e.g. gmd-3.0.0.jar)
that enables you to use Gmd from the command line.
```
java -jar gmd-3.0.0.jar toHtml test.gmd test.html
```
or for a pdf:
```
java -jar gmd-3.0.0.jar toPdf test.gmd test.pdf
```
Note: If you don't want the styled (highlight) PDF version you can use toPdfRaw instead.

## Using Gmd in Gradle

The gmd-gradle-plugin is a gradle plugin that allows you to use Gmd in your gradle build.
Usage is a follows:
```groovy
plugins {
  id 'se.alipsa.gmd.gmd-gradle-plugin'
}
gmdPlugin {
  sourceDir = 'src/test/gmd'
  targetDir = 'build/gmd'
  outputType = 'md' // or 'html' or 'pdf'
}
```
See the [gmd-gradle-plugin/readme](gmd-gradle-plugin/readme.md) for more details.

## Using Gmd in Maven
The gmd-maven-plugin is a maven plugin that allows you to use Gmd in your maven build.
Usage is a follows:
```xml
  <build>
    <plugins>
      <plugin>
        <groupId>se.alipsa.gmd</groupId>
        <artifactId>gmd-maven-plugin</artifactId>
        <goals><goal>processGmd</goal></goals>
        <configuration>
          <sourceDir>src/test/gmd</sourceDir>
          <targetDir>target/gmd</targetDir>
          <outputType>html</outputType>
        </configuration>
      </plugin>
    </plugins>
  </build>
```
See the [gmd-maven-plugin/readme](gmd-maven-plugin/readme.md) for more details.