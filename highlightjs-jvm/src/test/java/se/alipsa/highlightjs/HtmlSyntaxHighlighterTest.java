package se.alipsa.highlightjs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.jsoup.Jsoup.parse;

class HtmlSyntaxHighlighterTest {

  @Test
  void highlightsNamedAndAutoDetectedBlocks() {
    var highlighter = new HtmlSyntaxHighlighter();

    String html = highlighter.highlightCodeBlocks(
        "<pre><code class='language-groovy'>def answer = 42</code></pre>"
            + "<pre><code>select * from users</code></pre>");

    assertTrue(html.contains("class=\"language-groovy hljs\""));
    assertTrue(html.contains("hljs-keyword"));
    assertTrue(html.contains("hljs-number"));
    assertTrue(html.contains("hljs-built_in") || html.contains("hljs-keyword"));
  }

  @Test
  void skipsNoHighlightBlocks() {
    var highlighter = new HtmlSyntaxHighlighter();
    String html = "<pre><code class='nohighlight'>def answer = 42</code></pre>";

    String result = highlighter.highlightCodeBlocks(html);

    assertTrue(result.contains("class=\"nohighlight\""));
    assertFalse(result.contains("hljs-keyword"));
  }

  @Test
  void isIdempotentForAlreadyHighlightedBlocks() {
    var highlighter = new HtmlSyntaxHighlighter();
    String html = "<pre><code class='language-groovy'>def answer = 42</code></pre>";

    String once = highlighter.highlightCodeBlocks(html);
    String twice = highlighter.highlightCodeBlocks(once);

    assertEquals(once, twice);
  }

  @Test
  void preservesHeadWhenHighlightingFullDocument() {
    var highlighter = new HtmlSyntaxHighlighter();
    String html = "<!doctype html><html><head><title>Example</title></head>"
        + "<body><pre><code class='language-groovy'>def answer = 42</code></pre></body></html>";

    String result = highlighter.highlightCodeBlocks(html);
    var document = parse(result);

    assertEquals("Example", document.head().select("title").text());
    assertEquals(1, document.select("pre > code.hljs .hljs-number").size());
  }
}
