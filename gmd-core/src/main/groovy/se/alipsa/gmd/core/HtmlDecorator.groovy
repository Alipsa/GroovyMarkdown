package se.alipsa.gmd.core

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class HtmlDecorator {

  private static final Logger log = LogManager.getLogger(HtmlDecorator.class)

  public static final String HIGHLIGHT_JS_CSS_PATH = "/highlightJs/styles/default.min.css"
  public static final String HIGHLIGHT_JS_SCRIPT_PATH = "/highlightJs/highlight.min.js"
  // "/META-INF/resources/webjars/bootstrap/5.2.3/css/bootstrap.min.css"
  public static final String BOOTSTRAP_CSS_PATH = "/META-INF/resources/webjars/bootstrap/5.3.8/css/bootstrap.css"
  public static final String HIGHLIGHT_JS_INIT = "\n<script>hljs.highlightAll();</script>\n"
  public static final String HIGHLIGHT_JS_CSS = "\n<link rel='stylesheet' href='" + resourceUrlExternalForm(HIGHLIGHT_JS_CSS_PATH) + "'>\n"
  public static final String HIGHLIGHT_JS_SCRIPT = script(HIGHLIGHT_JS_SCRIPT_PATH) +
          script("/highlightJs/languages/groovy.min.js") +
          script("/highlightJs/languages/r.min.js") +
          script("/highlightJs/languages/java.min.js") +
          script("/highlightJs/languages/sas.min.js") +
          script("/highlightJs/languages/java.min.js") +
          script("/highlightJs/languages/json.min.js") +
          script("/highlightJs/languages/markdown.min.js") +
          script("/highlightJs/languages/python.min.js") +
          script("/highlightJs/languages/sql.min.js")

  public static final String BOOTSTRAP_CSS = resourceUrlExternalForm(BOOTSTRAP_CSS_PATH)

  public static final String HTML5_DECLARATION = "<!DOCTYPE html>\n"
  static final XHTML_MATHML_DOCTYPE = "<!DOCTYPE html PUBLIC\n \"-//OPENHTMLTOPDF//MATH XHTML Character Entities With MathML 1.0//EN\" \"\">\n"
  public static final String OPENHTMLTOPDF_DECLARATION = "<!DOCTYPE html PUBLIC\n\"-//OPENHTMLTOPDF//MATH XHTML Character Entities With MathML 1.0//EN\" \"\">\n"
  public static final String UNICODE_FONTS = """
      <style>
      
        @font-face {
          font-family: 'unicode';
          src: url('@unicodeUrl@/DejaVuSans.ttf');
          font-weight: normal;
          font-style: normal;
        }
        
        @font-face {
          font-family: 'noto-cjk';
          src: url('@arialuniUrl@/ArialUnicodeMS.ttf');
          font-weight: normal;
          font-style: normal;
        }
        
        @font-face {
          font-family: 'Roboto';
          src: url('@robotoUrl@/Roboto-Regular.ttf');
          font-weight: normal;
          font-style: normal;
        }
        
        @font-face {
          font-family: 'noto-mono';
          src: url('@courierPrimeUrl@/CourierPrime-Regular.ttf');
          font-weight: normal;
          font-style: normal;
        }
             
        /* Hide scrollbar but allow scrolling  */    
        body {
            font-family: 'Roboto', 'unicode', 'noto-cjk', sans-serif;
            overflow-y: scroll;
            scrollbar-width: none; /* Firefox */
            -ms-overflow-style: none;  /* Internet Explorer 10+ */
            word-wrap: break-word;
            font-size: 14px;
        }
        
        body::-webkit-scrollbar { /* WebKit */
          width: 0;
          height: 0;
        }
              
        var,
        code,
        kbd,
        pre {
            font: 0.9em 'noto-mono', Consolas, \\"Liberation Mono\\", Menlo, Courier, monospace;
        }
      </style>
      """.replaceAll("@robotoUrl@", resourceUrlExternalForm("/fonts/roboto"))
      .replaceAll("@arialuniUrl@", resourceUrlExternalForm("/fonts/arialuni"))
      .replaceAll("@courierPrimeUrl@", resourceUrlExternalForm("/fonts/courierprime"))
      .replaceAll("@unicodeUrl@", resourceUrlExternalForm("/fonts/DejaVu_Sans"))

  private static final String HIGHLIGHT_CUSTOM_STYLE = """
      <style>
        code { color: black }
        .hljs-string { color: DarkGreen }
        .hljs-number { color: MidnightBlue }
        .hljs-built_in { color: Maroon }
        .hljs-literal { color: MidnightBlue }
      </style>
      """.stripIndent()
  private static final String HIGHLIGHT_JS = getHighlightJs(true)
  private static final String BOOTSTRAP_STYLE = getBootstrapStyle(true)

  private static String HIGHLIGHT_STYLE = getHighlightStyle(true)

  private static String script(String path) {
    return  "<script src='" + resourceUrlExternalForm(path) + "'></script>\n"
  }

  private static String resourceUrlExternalForm(String resource) {
    URL url = HtmlDecorator.class.getResource(resource)
    return url == null ? "" : url.toExternalForm()
  }

  private static String resourceContent(String resource) {
    URL url = HtmlDecorator.class.getResource(resource)
    if (url == null) {
      throw new RuntimeException("${resource} does not exist")
    }
    return url.getText()
  }

  static String decorate(String html) {
    return decorate(html, false)
  }

  static String decorate(String html, boolean withMargin) {
    return decorate(
        html,
        OPENHTMLTOPDF_DECLARATION,
        withMargin,
        true,
        true,
        true
    )
  }

  static String decorate(String html, String docType, boolean withMargin, boolean withUnicodeFonts, boolean withHighlight, boolean withBootstrap) {
    StringBuilder sb = new StringBuilder(docType).append("<html>\n<head>\n<meta charset=\"UTF-8\">\n")
        if (withHighlight) {
          sb.append(HIGHLIGHT_STYLE)
              .append(HIGHLIGHT_CUSTOM_STYLE)
        }
        if (withBootstrap) {
          sb.append(BOOTSTRAP_STYLE)
        }
        if (withUnicodeFonts) {
          sb.append(UNICODE_FONTS)
        }
        if (withMargin) {
          sb.append("\n</head>\n<body style='margin-left: 15px; margin-right: 15px'>\n")
        } else {
          sb.append("\n</head>\n<body>\n")
        }
        sb.append(html).append("\n</body>\n")
        if (withHighlight) {
          sb.append(HIGHLIGHT_JS)
              .append(HIGHLIGHT_JS_INIT)
        }
        sb.append("\n</html>")
        return sb.toString()
  }

  private static String getHighlightStyle(boolean embed) {
    if (embed) {
      try {
        return "\n<style>\n" + resourceContent(HIGHLIGHT_JS_CSS_PATH) + "\n</style>\n"
      } catch (IOException e) {
        log.warn("Failed to get content of highlight css, falling back to external link.", e)
      }
    }
    return HIGHLIGHT_JS_CSS
  }

  private static String getHighlightJs(boolean embed) {
    if (embed) {
      try {
        return "\n<script>" + resourceContent(HIGHLIGHT_JS_SCRIPT_PATH) + "</script>\n"
      } catch (IOException e) {
        log.warn("Failed to get content of highlight js, falling back to external link.", e)
      }
    }
    return HIGHLIGHT_JS_SCRIPT
  }

  private static String getBootstrapStyle(boolean embed) {
    if (embed) {
      try {
        // @charset directive is not allowed when embedding the stylesheet
        String css = resourceContent(BOOTSTRAP_CSS_PATH).replace("@charset \"UTF-8\";", "\n")
        return "\n<style>\n" + css + "\n</style>\n"
      } catch (IOException e) {
        log.warn("Failed to read content to embed, resort to external ref instead", e)
      }
    }
    return "<link rel='stylesheet' href='" + BOOTSTRAP_CSS + "'>"
  }
}
