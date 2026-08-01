package se.alipsa.highlightjs;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.Locale;

/** Applies syntax highlighting to {@code <pre><code>} blocks in an HTML fragment or document. */
public final class HtmlSyntaxHighlighter {

  private final SyntaxHighlighter highlighter;

  public HtmlSyntaxHighlighter() {
    this(DefaultHolder.INSTANCE);
  }

  public HtmlSyntaxHighlighter(SyntaxHighlighter highlighter) {
    if (highlighter == null) {
      throw new IllegalArgumentException("Highlighter cannot be null");
    }
    this.highlighter = highlighter;
  }

  /**
   * Highlights code blocks while preserving whether the input is an HTML fragment or full document.
   * Fragments are returned as body content; full documents are returned with their head and body.
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
      String source = codeElement.wholeText();
      String highlighted = language == null
          ? highlighter.highlightAuto(source)
          : highlighter.highlight(source, language);

      codeElement.html(highlighted);
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
