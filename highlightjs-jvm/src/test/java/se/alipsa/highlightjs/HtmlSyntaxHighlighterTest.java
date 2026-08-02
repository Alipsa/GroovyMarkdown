package se.alipsa.highlightjs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.jsoup.Jsoup.parse;

class HtmlSyntaxHighlighterTest {

  @Test
  void highlightsBlocksWithAKnownLanguage() {
    var highlighter = new HtmlSyntaxHighlighter();

    String html = highlighter.highlightCodeBlocks(
        "<pre><code class='language-groovy'>def answer = 42</code></pre>");

    assertTrue(html.contains("class=\"language-groovy hljs\""));
    assertTrue(html.contains("hljs-keyword"));
    assertTrue(html.contains("hljs-number"));
  }

  @Test
  void leavesUntaggedBlocksUnhighlighted() {
    var highlighter = new HtmlSyntaxHighlighter();

    String html = highlighter.highlightCodeBlocks(
        "<pre><code>select the option and continue</code></pre>");

    assertFalse(html.contains("hljs-keyword"), "prose must not be auto-detected as code");
    assertTrue(html.contains("select the option and continue"));
    assertTrue(html.contains("class=\"hljs\""), "styling class should still be applied");
  }

  @Test
  void leavesUnknownLanguageBlocksUnhighlighted() {
    var highlighter = new HtmlSyntaxHighlighter();

    String html = highlighter.highlightCodeBlocks(
        "<pre><code class='language-no-such-language'>select the option</code></pre>");

    assertFalse(html.contains("hljs-keyword"));
    assertTrue(html.contains("select the option"));
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
