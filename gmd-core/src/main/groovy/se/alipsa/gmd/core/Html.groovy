package se.alipsa.gmd.core

import org.jsoup.nodes.Entities
import se.alipsa.groovy.svg.Svg
import se.alipsa.matrix.chartexport.ChartToSvg
import se.alipsa.matrix.pict.CharmBridge
import se.alipsa.matrix.pict.Chart
import se.alipsa.matrix.core.Matrix
import se.alipsa.matrix.xchart.abstractions.MatrixXChart

/**
 * This class makes i convenient to write Groovy code that creates html which is
 * useful for e.g. Munin groovy reports.
 */
class Html {

  StringWriter out = new StringWriter()

  Html add(String text) {
    out.println(text)
    return this
  }

  Html add(Matrix table, Map<String, String> htmlattr = [:]) {
    out.println(tableToHtml(table, htmlattr))
    return this
  }

  Html add(Chart chart, double width = 800, double height = 600, String alt = '', Map<String, String> htmlattr = [:]) {
    out.println(chartToHtml(chart, width, height, alt, htmlattr))
    return this
  }

  Html add(MatrixXChart chart, String alt = '', Map<String, String> htmlattr = [:]) {
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      chart.exportSvg(os)
      String imgContent = Base64.getEncoder().encodeToString(os.toByteArray())
      out.println(imgToHtml("data:image/svg+xml;base64,${imgContent}", alt, htmlattr))
    }
    return this
  }

  String toString() {
    return out.toString()
  }

  private static String chartToHtml(Chart x, double width, double height, String alt, Map<String, String> attributes) {
    Svg svg = CharmBridge.renderSvg(x, width as int, height as int)
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      ChartToSvg.export(svg, os)
      String imgContent = Base64.getEncoder().encodeToString(os.toByteArray())
      return imgToHtml("data:image/svg+xml;base64,${imgContent}", alt, attributes)
    }
  }

  /**
   * A bare <img> line starts a CommonMark HTML block, which swallows every
   * following line verbatim until a blank line. If this Html instance is
   * printed into a Markdown (.gmd) document, the trailing blank line here
   * terminates that block so the rest of the document keeps parsing as
   * Markdown.
   */
  private static String imgToHtml(String base64String, String alt, Map<String, String> attributes) {
    StringBuilder tag = new StringBuilder('<img alt="').append(escape(alt))
        .append('" src="').append(escape(base64String)).append('"')
    attributes.each {
      tag.append(' ').append(escape(it.key)).append('="').append(escape(it.value)).append('"')
    }
    tag.append(' />\n')
    return tag.toString()
  }

  private static String tableToHtml(Matrix table, Map<String, String> htmlattr) {
    StringBuilder sb = new StringBuilder()
    StringBuilder attr = new StringBuilder()
    if (htmlattr.size() > 0) {
      htmlattr.each {
        attr.append(it.key).append('="').append(escape(it.value)).append('" ')
      }
    }
    sb.append('<table ').append(attr).append('><thead><tr>')
    table.columnNames().each {
      sb.append('<th>').append(escape(it)).append('</th>')
    }
    sb.append('</tr></thead><tbody>')
    table.rows().each { row ->
      sb.append('<tr>')
      row.each { col ->
        sb.append('<td>').append(escape(col)).append('</td>')
      }
      sb.append('</tr>')
    }
    // Html.add uses println. Keep one newline here so the result has the
    // blank line CommonMark needs to terminate a table HTML block.
    sb.append('</tbody></table>\n')
    return sb.toString()
  }

  private static String escape(Object value) {
    return value == null ? '' : Entities.escape(value.toString())
  }
}
