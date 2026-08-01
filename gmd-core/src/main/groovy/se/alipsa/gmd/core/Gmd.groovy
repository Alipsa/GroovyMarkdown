package se.alipsa.gmd.core

import com.openhtmltopdf.mathmlsupport.MathMLDrawer
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import com.openhtmltopdf.util.XRLog
import se.alipsa.highlightjs.HtmlSyntaxHighlighter

import org.codehaus.groovy.control.CompilationFailedException
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.w3c.dom.Document

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import static se.alipsa.gmd.core.HtmlDecorator.decorate

/**
 * Key class for this Groovy Markdown implementation
 */
class Gmd {
  final Parser parser
  final HtmlRenderer renderer
  final HtmlSyntaxHighlighter htmlSyntaxHighlighter

  static void main(String[] args) {
    new GmdCommandLine(args).run()
  }

  Gmd() {
    XRLog.setLoggerImpl(new Log4jXRLogger());
    parser = Parser.builder().build()
    renderer = HtmlRenderer.builder()
        .softbreak("<br />\n")
        .extensions([TablesExtension.create()])
        .build()
    htmlSyntaxHighlighter = new HtmlSyntaxHighlighter()
  }

  /**
   * Process the Groovy Markdown text into a html document and write it to the File.
   *
   * @param gmd Groovy Markdown text
   * @param outFile the File to write to
   * @param bindings the variables to resolve in the text (optional)
   */
  void gmdToHtml(String gmd, File outFile, Map bindings = [:]) throws GmdException {
    try {
      writeUtf8(outFile, gmdToHtmlDoc(gmd, bindings))
    } catch (IOException e) {
      throw new GmdException("Failed to write to the file", e)
    }
  }

  /**
   * Process the Groovy Markdown text into a html document and write it to the Writer.
   *
   * @param gmd Groovy Markdown text
   * @param out the Writer to write to
   * @param bindings the variables to resolve in the text (optional)
   */
  void gmdToHtml(String gmd, Writer out, Map bindings = [:]) throws GmdException {
    try {
      out.write(gmdToHtmlDoc(gmd, bindings))
    } catch (IOException e) {
      throw new GmdException("Failed to write to the file", e)
    }
  }

  /**
   * Process the Groovy Markdown text and save it to the file.
   * The markdown is highlighted with Highlight.js and rendered directly to PDF.
   *
   * @param gmd Groovy Markdown text
   * @param file the File to write to
   * @param bindings the variables to resolve in the text (optional)
   */
  void gmdToPdf(String gmd, File file, Map bindings = [:]) throws GmdException {
    try {
      htmlToPdf(gmdToHtmlDoc(gmd, bindings), file)
    } catch (IOException e) {
      throw new GmdException('Failed to convert gmd to pdf', e)
    }
  }

  /**
   * Process the Groovy Markdown text into standard markdown
   * @param text
   * @param bindings the variables to resolve in the text
   * @return a markdown text document
   */
  String gmdToMd(String text, Map bindings) throws GmdException {
    try {
      return GmdTemplateEngine.processCodeBlocks(text, bindings)
    } catch (CompilationFailedException | ClassNotFoundException | IOException e) {
      throw new GmdException("Failed to process gmd", e)
    }
  }

  /**
   * Process the gmd into markdown and then into the html snippet.
   * This is useful e.g. for embedding that html in a larger html document.
   *
   * @param gmd the Groovy Markdown to process
   * @return the html equivalent of the gmd
   * @throws GmdException
   */
  String gmdToHtml(String gmd) throws GmdException {
    return mdToHtml(gmdToMd(gmd))
  }

  /**
   * Process the gmd into markdown and then into a (complete) html document.
   *
   * @param gmd the Groovy Markdown to process
   * @return the html equivalent of the gmd
   * @throws GmdException
   */
  String gmdToHtmlDoc(String gmd) throws GmdException {
    return mdToHtmlDoc(gmdToMd(gmd))
  }

  /**
   * Process the gmd into markdown and then into the html snippet.
   * This is useful e.g. for embedding that html in a larger html document.
   *
   * @param gmd the Groovy Markdown to process
   * @param bindings the variables to resolve in the text
   * @return the html equivalent of the gmd
   * @throws GmdException
   */
  String gmdToHtml(String gmd, Map bindings) throws GmdException {
    return mdToHtml(gmdToMd(gmd, bindings))
  }

  /**
   * Process the gmd into markdown and then into a (complete) html document.
   *
   * @param gmd the Groovy Markdown to process
   * @param bindings the variables to resolve in the text
   * @return the html equivalent of the gmd
   * @throws GmdException
   */
  String gmdToHtmlDoc(String gmd, Map bindings) throws GmdException {
    return mdToHtmlDoc(gmdToMd(gmd, bindings))
  }

  /**
   * Process the Groovy Markdown text into standard markdown
   * @param text
   * @return a markdown text document
   */
  String gmdToMd(String text) throws GmdException {
    return GmdTemplateEngine.processCodeBlocks(text)
  }

  String mdToHtml(String markdown) throws GmdException {
    org.commonmark.node.Node document = parser.parse(markdown)
    return renderer.render(document)
  }

  String mdToHtmlDoc(String markdown) throws GmdException {
    return decorate(htmlSyntaxHighlighter.highlightCodeBlocks(mdToHtml(markdown)))
  }

  void mdToHtml(String markdown, File target) {
    try {
      writeUtf8(target, renderer.render(parser.parse(markdown)))
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write HTML", e)
    }
  }

  void mdToHtmlDoc(String markdown, File target) {
    if (target == null) {
      throw new IllegalArgumentException("target file cannot be null")
    }
    try {
      writeUtf8(target, mdToHtmlDoc(markdown))
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write HTML document", e)
    }
  }

  void mdToPdf(String md, File target) throws GmdException {
    try (def out = Files.newOutputStream(target.toPath())) {
      mdToPdf(md, out)
    } catch (Exception e) {
      throw new GmdException("Failed to convert markdown to pdf", e)
    }
  }

  void mdToPdf(String md, OutputStream target) throws GmdException {
    htmlToPdf(mdToHtmlDoc(md), target)
  }

  void htmlToPdf(String html, OutputStream target) {
    var jsDoc = Jsoup.parse(html)
    Document doc = new W3CDom().fromJsoup(jsDoc)
    htmlToPdf(doc, target)
  }

  void htmlToPdf(String html, File file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("File parameter cannot be null")
    }
    ensureParentDirectory(file)
    try (OutputStream out = Files.newOutputStream(file.toPath())) {
      htmlToPdf(html, out)
    }
  }

  void htmlToPdf(Document doc, OutputStream os) throws IOException {
    if (doc == null) {
      throw new IllegalArgumentException("Document parameter cannot be null")
    }
    if (os == null) {
      throw new IllegalArgumentException("Output stream cannot be null")
    }
    PdfRendererBuilder builder = new PdfRendererBuilder()
        .useSVGDrawer(new BatikSVGDrawer())
        .useMathMLDrawer(new MathMLDrawer())
        .withW3cDocument(doc, new File(".").toURI().toString())
        .toStream(os)
    builder.run()
  }

  /**
   * Highlight HTML code blocks and save the result as a PDF.
   *
   * @deprecated Highlighting is synchronous now. Use {@link #htmlToPdf(String, File)}.
   * @param html the HTML content to highlight and render
   * @param target the PDF output file
   * @param ignoredExitOnFinish retained for compatibility and ignored
   */
  @Deprecated
  void processHtmlAndSaveAsPdf(String html, File target, boolean ignoredExitOnFinish = false) throws GmdException {
    if (html == null) {
      throw new IllegalArgumentException("Html content cannot be null")
    }
    if (target == null) {
      throw new IllegalArgumentException("Target file cannot be null")
    }
    try {
      htmlToPdf(htmlSyntaxHighlighter.highlightCodeBlocks(html), target)
    } catch (IOException e) {
      throw new GmdException('Failed to convert HTML to PDF', e)
    }
  }

  private static void writeUtf8(File target, String content) throws IOException {
    if (target == null) {
      throw new IllegalArgumentException("Target file cannot be null")
    }
    ensureParentDirectory(target)
    Files.writeString(target.toPath(), content, StandardCharsets.UTF_8)
  }

  private static void ensureParentDirectory(File target) throws IOException {
    File parent = target.parentFile
    if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
      throw new IOException("Could not create parent directory " + parent.absolutePath)
    }
  }
}
