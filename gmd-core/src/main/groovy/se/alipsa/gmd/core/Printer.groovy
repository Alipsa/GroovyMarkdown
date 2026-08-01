package se.alipsa.gmd.core

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
        print(x.toHtml(tableAttributes))
    }

    void println(Matrix x, Map<String,String> tableAttributes) {
        println(x.toHtml(tableAttributes))
    }

    void print(Matrix x) {
        print(x, ["class": "table"])
    }

    void println(Matrix x) {
        println(x, ["class": "table"])
    }


    private static String chartToMd(Chart x, double width, double height, String alt, Map<String, String> attributes) {
        StringBuilder attr = new StringBuilder()
        if (attributes.size() > 0) {
            attr.append('{')
            attributes.each {
                attr.append(it.key).append('=').append(it.value).append(' ')
            }
            attr.append('}')
        }
        Svg svg = CharmBridge.renderSvg(x, width as int, height as int)
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ChartToSvg.export(svg, os)
            String imgContent = Base64.getEncoder().encodeToString(os.toByteArray())
            return "!['${alt}'](data:image/svg+xml;base64,${imgContent})${attr.toString()}"
        }
    }

    private static String chartToMd(MatrixXChart x, String alt, Map<String, String> attributes) {
        StringBuilder attr = new StringBuilder()
        if (attributes.size() > 0) {
            attr.append('{')
            attributes.each {
                attr.append(it.key).append('=').append(it.value).append(' ')
            }
            attr.append('}')
        }
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            x.exportSvg(os)
            String imgContent = Base64.getEncoder().encodeToString(os.toByteArray())
            return "!['${alt}'](data:image/svg+xml;base64,${imgContent})${attr.toString()}"
        }
    }

    void print(Chart x, double width = 800, double height = 600, String alt = '', Map<String, String> attributes = [:]) {
        print(chartToMd(x, width, height, alt, attributes))
    }

    void println(Chart x, double width = 800, double height = 600, String alt = '', Map<String, String> attributes = [:]) {
        println(chartToMd(x, width, height, alt, attributes))
    }

    void print(MatrixXChart x, String alt = '', Map<String, String> attributes = [:]) {
        print(chartToMd(x, alt, attributes))
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
