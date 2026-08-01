package se.alipsa.highlightjs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
  void skipsNoHighlightBlocksAndIsIdempotent() {
    var highlighter = new HtmlSyntaxHighlighter();
    String html = "<pre><code class='nohighlight'>def answer = 42</code></pre>";

    String result = highlighter.highlightCodeBlocks(html);

    assertTrue(result.contains("class=\"nohighlight\""));
    assertFalse(result.contains("hljs-keyword"));
  }
}
