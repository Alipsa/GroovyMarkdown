package se.alipsa.gmd.core


import se.alipsa.matrix.charts.Chart
import se.alipsa.matrix.charts.Plot
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
      chart.exportPng(os)
      String imgContent = Base64.getEncoder().encodeToString(os.toByteArray())
      out.print("data:image/png;base64,")
      out.println(imgToHtml(imgContent, alt, htmlattr))
    }
    return this
  }

  String toString() {
    return out.toString()
  }

  private static String chartToHtml(Chart x, double width, double height, String alt, Map<String, String> attributes) {
    imgToHtml(Plot.base64(x, width, height), alt, attributes )
  }

  private static String imgToHtml(String base64String, String alt, Map<String, String> attributes) {
    StringBuilder attr = new StringBuilder()
    if (attributes.size() > 0) {
      attributes.each {
        attr.append(it.key).append('=').append(it.value).append(' ')
      }
    }
    return "<img alt='${alt}' src='${base64String}' ${attr.toString()} />"
  }

  private static String tableToHtml(Matrix table, Map<String, String> htmlattr) {
    StringBuilder sb = new StringBuilder()
    StringBuilder attr = new StringBuilder()
    if (htmlattr.size() > 0) {
      htmlattr.each {
        attr.append(it.key).append('=').append(it.value).append(' ')
      }
    }
    sb.append('<table ').append(attr).append('><thead><tr>')
    table.columnNames().each {
      sb.append('<th>').append(it).append('<th>')
    }
    sb.append('</tr></thead><tbody>')
    table.rows().each { row ->
      sb.append('<tr>')
      row.each { col ->
        sb.append('<td>').append(col).append('</td>')
      }
      sb.append('</tr>')
    }
    sb.append('</tbody></table>')
    return sb.toString()
  }
}
