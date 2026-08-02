package test.alipsa.groovy.gmd

import org.junit.jupiter.api.Test
import se.alipsa.gmd.core.Html
import se.alipsa.matrix.core.Matrix

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class HtmlTest {

  private static Matrix sample() {
    Matrix.builder().data(a: [1, 2], b: ['x', 'y']).types(int, String).build()
  }

  @Test
  void closesHeaderCells() {
    String html = new Html().add(sample()).toString()

    assertTrue(html.contains('<th>a</th>'), "Header cells must be closed: $html")
    assertTrue(html.contains('<th>b</th>'), "Header cells must be closed: $html")
    assertFalse(html.contains('<th>a<th>'), "Stray opening th tag: $html")
  }

  @Test
  void quotesAttributeValues() {
    String html = new Html().add(sample(), ['class': 'table table-striped']).toString()

    assertTrue(html.contains('class="table table-striped"'),
        "Attribute values must be quoted: $html")
  }

  @Test
  void escapesTableAndAttributeContent() {
    Matrix table = Matrix.builder().data(a: ['<b>']).types(String).build()

    String html = new Html().add(table, ['title': 'a "quoted" value']).toString()

    assertTrue(html.contains('title="a &quot;quoted&quot; value"'), html)
    assertTrue(html.contains('<td>&lt;b&gt;</td>'), html)
  }

  @Test
  void rendersRows() {
    String html = new Html().add(sample()).toString()

    assertTrue(html.contains('<td>1</td>'))
    assertTrue(html.contains('<td>y</td>'))
  }

  @Test
  void addReturnsItselfForChaining() {
    Html html = new Html()

    assertEquals(html, html.add('one').add('two'))
    assertTrue(html.toString().contains('one'))
    assertTrue(html.toString().contains('two'))
  }
}
