package se.alipsa.gmd.core

import com.openhtmltopdf.mathmlsupport.MathMLDrawer
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
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
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.jsoup.nodes.Entities
import org.w3c.dom.Document

import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

import static se.alipsa.gmd.core.HtmlDecorator.BOOTSTRAP_CSS

/**
 * JavaFX-backed PDF renderer. This class is deliberately referenced only when
 * styled PDF output is requested; normal Gmd construction and raw PDF output
 * do not load it.
 */
class StyledPdfRenderer {

  private static final Logger LOG = LogManager.getLogger(StyledPdfRenderer.class)
  private static final String JAVAFX_VERSION = '23.0.2'
  private static final int TIMEOUT_SECONDS = 15

  static void render(String html, File target, boolean exitOnFinish = false) throws GmdException {
    ensureJavaFxAvailable()
    if (html == null) {
      throw new IllegalArgumentException('Html content cannot be null')
    }
    if (target == null) {
      throw new IllegalArgumentException('Target file cannot be null')
    }

    try {
      //noinspection GroovyResultOfObjectAllocationIgnored
      new JFXPanel() // Initializes the JavaFX toolkit.
      CountDownLatch completed = new CountDownLatch(1)
      AtomicReference<Throwable> failure = new AtomicReference<>()
      Platform.runLater {
        try {
          // Keep this render's WebView alive through the listener closure;
          // concurrent renders must never share mutable WebView state.
          WebView view = new WebView()
          loadAndSavePdf(html, target, view, failure, completed)
        } catch (Throwable t) {
          failure.set(t)
          completed.countDown()
        }
      }

      boolean finished = completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      if (!finished) {
        throw new TimeoutException("JavaFX WebView did not finish loading within ${TIMEOUT_SECONDS} seconds")
      }
      if (failure.get() != null) {
        throw new GmdException('Failed to process HTML in the JavaFX WebView', failure.get())
      }
    } catch (GmdException e) {
      throw e
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt()
      throw new GmdException('Interrupted while waiting for the JavaFX WebView', e)
    } catch (TimeoutException e) {
      throw new GmdException('Timed out while processing HTML in the JavaFX WebView', e)
    } catch (Throwable t) {
      throw new GmdException('Could not initialize or use JavaFX for styled PDF output', t)
    } finally {
      if (exitOnFinish) {
        try {
          Platform.exit()
        } catch (Throwable t) {
          LOG.debug('Unable to stop JavaFX after styled PDF rendering', t)
        }
      }
    }
  }

  private static void ensureJavaFxAvailable() throws GmdException {
    if (javaFxClassAvailable()) {
      return
    }

    try {
      GroovyClassLoader classLoader = findGroovyClassLoader()
      Map<String, Object> common = [
          classLoader     : classLoader,
          transitive      : true,
          autoDownload    : true,
          initClassLoader : true
      ]
      String platform = platformClassifier()
      ['javafx-base', 'javafx-graphics', 'javafx-controls', 'javafx-swing', 'javafx-web'].each { module ->
        Grape.grab(common + [
            group     : 'org.openjfx',
            module    : module,
            version   : JAVAFX_VERSION,
            classifier: platform
        ])
      }
    } catch (Throwable t) {
      throw new GmdException('Styled PDF output requires JavaFX, but its dependencies could not be loaded', t)
    }

    if (!javaFxClassAvailable()) {
      throw new GmdException('Styled PDF output requires JavaFX (javafx-swing and javafx-web) on the runtime classpath')
    }
  }

  private static boolean javaFxClassAvailable() {
    try {
      Class.forName('javafx.embed.swing.JFXPanel', false, StyledPdfRenderer.class.classLoader)
      Class.forName('javafx.scene.web.WebView', false, StyledPdfRenderer.class.classLoader)
      return true
    } catch (ClassNotFoundException | LinkageError ignored) {
      return false
    }
  }

  private static GroovyClassLoader findGroovyClassLoader() {
    if (StyledPdfRenderer.class.classLoader instanceof GroovyClassLoader) {
      return StyledPdfRenderer.class.classLoader as GroovyClassLoader
    }
    if (Thread.currentThread().contextClassLoader instanceof GroovyClassLoader) {
      return Thread.currentThread().contextClassLoader as GroovyClassLoader
    }
    GroovyClassLoader classLoader = new GroovyClassLoader(StyledPdfRenderer.class.classLoader)
    Thread.currentThread().contextClassLoader = classLoader
    return classLoader
  }

  private static String platformClassifier() {
    String osName = System.getProperty('os.name', '').toLowerCase(Locale.ROOT)
    String osArch = System.getProperty('os.arch', '').toLowerCase(Locale.ROOT)
    if (osName.contains('mac') || osName.contains('darwin')) {
      return (osArch.contains('aarch64') || osArch.contains('arm')) ? 'mac-aarch64' : 'mac'
    }
    if (osName.contains('linux')) {
      return 'linux'
    }
    if (osName.contains('win')) {
      return 'win'
    }
    throw new IllegalStateException("Unsupported operating system: ${osName}")
  }

  private static void loadAndSavePdf(String html, File target, WebView view,
                                     AtomicReference<Throwable> failure, CountDownLatch completed) {
    WebEngine engine = view.engine
    engine.javaScriptEnabled = true
    engine.userStyleSheetLocation = BOOTSTRAP_CSS
    engine.loadWorker.stateProperty().addListener(new ChangeListener<Worker.State>() {
      @Override
      void changed(ObservableValue observable, Worker.State oldState, Worker.State newState) {
        // Referencing view here keeps this render's WebView strongly reachable
        // until the asynchronous load has completed. Keep terminal failures
        // visible at the default log level for slow or failed PDF diagnostics.
        if (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
          LOG.warn('WebView {} loading HTML document ended in state {}', view, newState)
        } else {
          LOG.debug('WebView {} loading HTML document, state is {}', view, newState)
        }
        if (newState == Worker.State.SUCCEEDED) {
          try {
            Document document = engine.document
            def transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, 'no')
            transformer.setOutputProperty(OutputKeys.METHOD, 'html')
            transformer.setOutputProperty(OutputKeys.INDENT, 'no')
            transformer.setOutputProperty(OutputKeys.ENCODING, 'UTF-8')

            StringWriter writer = new StringWriter()
            transformer.transform(new DOMSource(document), new StreamResult(writer))

            // The raw WebView DOM is not accepted by PdfRendererBuilder, so
            // parse the serialized DOM once more through jsoup.
            def parsed = Jsoup.parse(writer.toString())
            parsed.outputSettings()
                .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.extended)
                .charset(StandardCharsets.UTF_8)
                .prettyPrint(false)
            Document pdfDocument = new W3CDom().fromJsoup(parsed)
            ensureParentDirectory(target)
            try (OutputStream output = Files.newOutputStream(target.toPath())) {
              new PdfRendererBuilder()
                  .useSVGDrawer(new BatikSVGDrawer())
                  .useMathMLDrawer(new MathMLDrawer())
                  .withW3cDocument(pdfDocument, new File('.').toURI().toString())
                  .toStream(output)
                  .run()
            }
          } catch (Throwable t) {
            LOG.warn('Styled PDF rendering failed', t)
            failure.set(t)
          } finally {
            completed.countDown()
          }
        } else if (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
          Throwable loadFailure = engine.loadWorker.exception
          failure.set(loadFailure ?: new IllegalStateException("WebView load ended in state ${newState}"))
          completed.countDown()
        }
      }
    })
    engine.loadContent(html)
  }

  private static void ensureParentDirectory(File target) throws IOException {
    File parent = target.parentFile
    if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
      throw new IOException("Could not create parent directory ${parent.absolutePath}")
    }
  }
}
