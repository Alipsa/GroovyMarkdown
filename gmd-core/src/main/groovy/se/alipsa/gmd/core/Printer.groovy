package se.alipsa.gmd.core

import org.jsoup.nodes.Entities
import se.alipsa.groovy.svg.Svg
import se.alipsa.matrix.chartexport.ChartToSvg
import se.alipsa.matrix.pict.CharmBridge;
import se.alipsa.matrix.pict.Chart;
import se.alipsa.matrix.core.Matrix
import se.alipsa.matrix.xchart.abstractions.MatrixXChart;

class Printer extends PrintWriter {

    Printer() {
        super(new StringWriter());
    }

    void print(Character[] x) {
        x.each {
            print(it)
        }
    }
    /**
     * Prints an array of characters and then terminates the line.  This method
     * behaves as though it invokes {@link #print(char[])} and then
     * {@link #println()}.
     *
     * @param x the array of {@code char} values to be printed
     */
    void println(Character[] x) {
        print(x)
        println()
    }

    void print(Matrix x, Map<String,String> tableAttributes) {
        // The Table extension in commonmark does not support custom attributes so we use toHtml as a work around
        print(terminateHtmlBlock(x.toHtml(tableAttributes)))
    }

    void println(Matrix x, Map<String,String> tableAttributes) {
        print(terminateHtmlBlock(x.toHtml(tableAttributes)))
        println()
    }

    void print(Matrix x) {
        print(x, ["class": "table"])
    }

    void println(Matrix x) {
        println(x, ["class": "table"])
    }


    private static String chartToMd(Chart x, double width, double height, String alt, Map<String, String> attributes) {
        Svg svg = CharmBridge.renderSvg(x, width as int, height as int)
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ChartToSvg.export(svg, os)
            String imgContent = Base64.getEncoder().encodeToString(os.toByteArray())
            return imgToHtml("data:image/svg+xml;base64,${imgContent}", alt, attributes)
        }
    }

    private static String chartToMd(MatrixXChart x, String alt, Map<String, String> attributes) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            x.exportSvg(os)
            String imgContent = Base64.getEncoder().encodeToString(os.toByteArray())
            return imgToHtml("data:image/svg+xml;base64,${imgContent}", alt, attributes)
        }
    }

    /**
     * CommonMark's image syntax has no attribute extension, so Pandoc-style
     * {key=value} suffixes are left as literal text in the output. Emit raw
     * <img> HTML instead, which CommonMark passes through unchanged.
     *
     * A bare <img> line starts a CommonMark HTML block, which swallows every
     * following line verbatim until a blank line. print adds that terminating
     * newline only when the image starts a line; an image printed after prose
     * remains inline. println always adds its own line separator.
     */
    private static String imgToHtml(String src, String alt, Map<String, String> attributes) {
        StringBuilder tag = new StringBuilder('<img alt="').append(escape(alt))
            .append('" src="').append(escape(src)).append('"')
        attributes.each {
            tag.append(' ').append(escape(it.key)).append('="').append(escape(it.value)).append('"')
        }
        tag.append(' />\n')
        return tag.toString()
    }

    private static String escape(Object value) {
        return value == null ? '' : Entities.escape(value.toString())
    }

    private static String terminateHtmlBlock(String html) {
        return html.endsWith('\n') ? html + '\n' : html + '\n\n'
    }

    private boolean isAtLineStart() {
        String content = toString()
        return content.isEmpty() || content.endsWith('\n') || content.endsWith('\r')
    }

    private void printChart(String html) {
        print(isAtLineStart() ? terminateHtmlBlock(html) : html)
    }

    void print(Chart x, double width = 800, double height = 600, String alt = '', Map<String, String> attributes = [:]) {
        printChart(chartToMd(x, width, height, alt, attributes))
    }

    void println(Chart x, double width = 800, double height = 600, String alt = '', Map<String, String> attributes = [:]) {
        println(chartToMd(x, width, height, alt, attributes))
    }

    void print(MatrixXChart x, String alt = '', Map<String, String> attributes = [:]) {
        printChart(chartToMd(x, alt, attributes))
    }

    void println(MatrixXChart x, String alt = '', Map<String, String> attributes = [:]) {
        println(chartToMd(x, alt, attributes))
    }

    @Override
    String toString() {
        return super.out.toString();
    }

    void clear() {
        ((StringWriter)super.out).getBuffer().setLength(0);
    }
}
