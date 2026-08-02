package test.alipsa.groovy.gmd

import org.junit.jupiter.api.Test
import se.alipsa.gmd.core.GmdProcessor

import static org.junit.jupiter.api.Assertions.assertTrue

class GmdProcessorTest extends AbstractGmdTest {

  private static File sourceDirWith(String name, String content) {
    File dir = new File(AbstractGmdTest.testOutputDir, "src-${name}")
    dir.mkdirs()
    new File(dir, "${name}.gmd").text = content
    return dir
  }

  @Test
  void htmlOutputIsACompleteDocument() {
    File src = sourceDirWith('doc', "# Hi\n\n```{groovy}\nout.println('x')\n```\n")
    File target = new File(AbstractGmdTest.testOutputDir, 'out-doc')

    new GmdProcessor().process(src.absolutePath, target.absolutePath, 'html')

    String html = new File(target, 'doc.html').text
    assertTrue(html.startsWith('<!DOCTYPE html PUBLIC'), "Missing doctype:\n$html")
    assertTrue(html.contains('<style>'), 'Missing embedded stylesheets')
    assertTrue(html.contains('<h1>Hi</h1>'), 'Missing rendered content')
    assertTrue(html.contains('hljs'), 'Code blocks were not highlighted')
  }

  @Test
  void mdOutputIsStillPlainMarkdown() {
    File src = sourceDirWith('plain', "# Hi\n\n```{groovy echo=false}\nout.println('x')\n```\n")
    File target = new File(AbstractGmdTest.testOutputDir, 'out-plain')

    new GmdProcessor().process(src.absolutePath, target.absolutePath, 'md')

    String md = new File(target, 'plain.md').text
    assertTrue(md.contains('# Hi'))
    assertTrue(!md.contains('<!DOCTYPE'), 'Markdown output must not be decorated')
  }
}
