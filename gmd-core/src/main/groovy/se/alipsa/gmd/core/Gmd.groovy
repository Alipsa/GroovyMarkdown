package se.alipsa.gmd.core

import com.openhtmltopdf.mathmlsupport.MathMLDrawer
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import com.openhtmltopdf.util.XRLog
import groovy.grape.Grape
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.codehaus.groovy.control.CompilationFailedException
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.jsoup.nodes.Entities
import org.w3c.dom.Document

import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static HtmlDecorator.BOOTSTRAP_CSS
import static HtmlDecorator.decorate

/**
 * Key class for this Groovy Markdown implementation
 */
class Gmd {

  static {
    GroovyClassLoader cl
    if (Gmd.class.classLoader instanceof GroovyClassLoader) {
      cl = Gmd.class.classLoader
    } else if (Thread.currentThread().getContextClassLoader() instanceof GroovyClassLoader) {
      cl = Thread.currentThread().getContextClassLoader()
    } else {
      // Build a Groovy-aware loader and make it the context loader
      cl = new GroovyClassLoader(Gmd.class.classLoader)
      Thread.currentThread().setContextClassLoader(cl)
    }

    // Use the single-map form so Groovy binds to grab(Map) and NEVER calls chooseClassLoader
    Map<String, Object> common = [
        classLoader     : cl,
        transitive      : true,
        autoDownload    : true,
        initClassLoader : true
    ]
    // We fetch javafx using Grab as doing
    // implementation "org.openjfx:javafx-base:${javaFxVersion}:${qualifier}"
    // in in build script makes the fatJar os dependent
    Grape.grab(common + [group:'org.openjfx', module:'javafx-controls', version:'23.0.2'])
    Grape.grab(common + [group:'org.openjfx', module:'javafx-swing',    version:'23.0.2'])

  }

  private static final Logger LOG = LogManager.getLogger(Gmd.class)
  final Parser parser
  final HtmlRenderer renderer
  WebView webView // We need to make the webView a member variable otherwise it is destroyed by GC

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
      outFile.write(gmdToHtmlDoc(gmd, bindings))
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
   * The markdown is processed in a Javafx WebView to enable javascript styling.
   *
   * @param gmd Groovy Markdown text
   * @param file the File to write to
   * @param bindings the variables to resolve in the text (optional)
   */
  void gmdToPdf(String gmd, File file, Map bindings = [:]) throws GmdException {
    //htmlToPdf(gmdToHtmlDoc(gmd, bindings), file)
    processHtmlAndSaveAsPdf(gmdToHtmlDoc(gmd, bindings), file)
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
    return decorate(mdToHtml(markdown))
  }

  void mdToHtml(String markdown, File target) {
    target.write(renderer.render(parser.parse(markdown)))
  }

  void mdToHtmlDoc(String markdown, File target) {
    if (target == null) {
      throw new IllegalArgumentException("target file cannot be null")
    }
    target.write(mdToHtmlDoc(markdown))
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
    if (file.getParentFile() != null && !file.getParentFile().exists()) {
      file.getParentFile().mkdirs()
    }
    if (!file.exists()) {
      file.createNewFile()
    }
    try (OutputStream out = Files.newOutputStream(file.toPath())) {
      htmlToPdf(html, out)
    }
  }

  void htmlToPdf(Document doc, OutputStream os) throws IOException {
    PdfRendererBuilder builder = new PdfRendererBuilder()
        .useSVGDrawer(new BatikSVGDrawer())
        .useMathMLDrawer(new MathMLDrawer())
        .withW3cDocument(doc, new File(".").toURI().toString())
        .toStream(os)
    builder.run()
  }

  /**
   * Load the html into a web view so that the highlight javascript properly add classes to code parts
   * then we extract the DOM from the web view and use that to produce the PDF
   *
   * @param html a string containing the html to render
   * @param target the pdf output stream to write to
   * @param exitOnFinish execute Platform.exit() on completion
   */
  void processHtmlAndSaveAsPdf(String html, File target, boolean exitOnFinish = false) throws GmdException {
    if (html == null) {
      throw new IllegalArgumentException("Html content cannot be null")
    }
    if (target == null) {
      throw new IllegalArgumentException("Target file cannot be null")
    }
    //noinspection GroovyResultOfObjectAllocationIgnored
    new JFXPanel() // Initiate graphics
    final CountDownLatch latchToWaitForJavaFx = new CountDownLatch(1)
    final AtomicReference<Throwable> exc = new AtomicReference<>(null)
    Platform.runLater {
      webView = new WebView()
      loadAndSavePdf(html, target, webView, exc, latchToWaitForJavaFx)
    }
    latchToWaitForJavaFx.await(15, TimeUnit.SECONDS)
    if (exitOnFinish) {
      Platform.exit()
    }
    if (exc.get() != null) {
      throw new GmdException("Failed to process html in the WebView", exc.get())
    }
  }

  void loadAndSavePdf(String html, File target, WebView webView, AtomicReference<Throwable> exc, CountDownLatch latch) {
    final WebEngine webEngine = webView.getEngine()
    webEngine.setJavaScriptEnabled(true)
    webEngine.setUserStyleSheetLocation(BOOTSTRAP_CSS)
    webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
      @Override
      void changed(ObservableValue ov, Worker.State oldState, Worker.State newState) {
        LOG.info("loading html document, state is {}", newState)
        if (newState == Worker.State.SUCCEEDED) {
          try {
            Document doc = webEngine.getDocument()
            Transformer transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            transformer.setOutputProperty(OutputKeys.METHOD, "html")
            transformer.setOutputProperty(OutputKeys.INDENT, "no")
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")

            StringWriter sw = new StringWriter()
            transformer.transform(new DOMSource(doc), new StreamResult(sw))
            String viewContent = sw.toString()
            // the raw DOM document will not work so we have to parse it again with jsoup to get
            // something that the PdfRendererBuilder (used in gmd) understands
            org.jsoup.nodes.Document doc2 = Jsoup.parse(viewContent)
            doc2.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.extended)
                .charset(StandardCharsets.UTF_8)
                .prettyPrint(false)
            Document doc3 = new W3CDom().fromJsoup(doc2)
            try (FileOutputStream fos = new FileOutputStream(target)) {
              PdfRendererBuilder builder = new PdfRendererBuilder()
                  .useSVGDrawer(new BatikSVGDrawer())
                  .useMathMLDrawer(new MathMLDrawer())
                  .withW3cDocument(doc3, new File(".").toURI().toString())
                  .toStream(fos)
              builder.run()
            }
          } catch (Throwable t) {
            LOG.warn(t)
            exc.set(t)
          } finally {
            latch.countDown()
          }
        }
      }
    })
    webEngine.loadContent(html)
  }
}
