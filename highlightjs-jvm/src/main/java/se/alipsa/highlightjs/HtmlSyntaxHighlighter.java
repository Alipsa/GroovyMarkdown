package se.alipsa.highlightjs;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Applies syntax highlighting to {@code <pre><code>} blocks in an HTML fragment. */
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

  public String highlightCodeBlocks(String html) {
    if (html == null) {
      throw new IllegalArgumentException("HTML content cannot be null");
    }

    var document = Jsoup.parseBodyFragment(html);
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
    return document.body().html();
  }

  private static final class DefaultHolder {
    private static final SyntaxHighlighter INSTANCE = new HighlightJsHighlighter();
  }
}
