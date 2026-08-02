package se.alipsa.highlightjs;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.Locale;

/** Applies syntax highlighting to {@code <pre><code>} blocks in an HTML fragment or document. */
public final class HtmlSyntaxHighlighter {

  private final SyntaxHighlighter highlighter;

  /** Creates an HTML highlighter using the bundled Highlight.js runtime. */
  public HtmlSyntaxHighlighter() {
    this(DefaultHolder.INSTANCE);
  }

  /**
   * Creates an HTML highlighter using the supplied source highlighter.
   *
   * @param highlighter source highlighter to use, never null
   * @throws IllegalArgumentException if {@code highlighter} is null
   */
  public HtmlSyntaxHighlighter(SyntaxHighlighter highlighter) {
    if (highlighter == null) {
      throw new IllegalArgumentException("Highlighter cannot be null");
    }
    this.highlighter = highlighter;
  }

  /**
   * Highlights code blocks while preserving whether the input is an HTML fragment or full document.
   * Fragments are returned as body content; full documents are returned with their head and body.
   *
   * @param html HTML fragment or document containing {@code <pre><code>} blocks
   * @return the HTML with supported code blocks highlighted
   * @throws IllegalArgumentException if {@code html} is null
   */
  public String highlightCodeBlocks(String html) {
    if (html == null) {
      throw new IllegalArgumentException("HTML content cannot be null");
    }

    boolean fullDocument = isFullDocument(html);
    var document = fullDocument ? Jsoup.parse(html) : Jsoup.parseBodyFragment(html);
    for (Element codeElement : document.select("pre > code")) {
      if (codeElement.hasClass("nohighlight")
          || codeElement.hasClass("no-highlight")
          || codeElement.hasClass("hljs")) {
        continue;
      }

      String language = codeElement.classNames().stream()
          .filter(name -> name.startsWith("language-"))
          .map(name -> name.substring("language-".length()))
          .findFirst()
          .orElse(null);
      String highlighted = highlighter.highlight(codeElement.wholeText(), language);
      if (highlighted != null) {
        codeElement.html(highlighted);
      }
      codeElement.addClass("hljs");
    }
    return fullDocument ? document.outerHtml() : document.body().html();
  }

  private static boolean isFullDocument(String html) {
    String trimmed = html.stripLeading().toLowerCase(Locale.ROOT);
    return trimmed.startsWith("<!doctype") || trimmed.startsWith("<html");
  }

  private static final class DefaultHolder {
    private static final SyntaxHighlighter INSTANCE = new HighlightJsHighlighter();
  }
}
