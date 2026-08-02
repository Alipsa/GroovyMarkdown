package se.alipsa.highlightjs;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighlightJsHighlighterTest {

  private final SyntaxHighlighter highlighter = new HighlightJsHighlighter();

  @Test
  void highlightsLanguagesThatWereRemovedIn31() {
    assertTrue(highlighter.highlight("if [ -f x ]; then echo hi; fi", "bash")
        .contains("hljs-keyword"), "bash should be registered");
    assertTrue(highlighter.highlight("object M { def f(a: Int): Int = a }", "scala")
        .contains("hljs-keyword"), "scala should be registered");
    assertTrue(highlighter.highlight("auto x = std::vector<int>{1};", "cpp")
        .contains("hljs-keyword"), "cpp should be registered");
    assertTrue(highlighter.highlight("fn main() { let x = 1; }", "rust")
        .contains("hljs-keyword"), "rust should be registered");
    assertTrue(highlighter.highlight("fun main() { val x = 1 }", "kotlin")
        .contains("hljs-keyword"), "kotlin should be registered");
  }

  @Test
  void keepsPreviouslySupportedLanguages() {
    assertTrue(highlighter.highlight("def a = 1", "groovy").contains("hljs-keyword"));
    assertTrue(highlighter.highlight("select * from t", "sql").contains("hljs-keyword"));
  }

  @Test
  void everyBundledGrammarIsRegistered() {
    File languageDir = new File(System.getProperty("basedir", "."), "source/highlightJs/languages");
    assertTrue(languageDir.isDirectory(), "Missing grammar source dir " + languageDir.getAbsolutePath());

    File[] grammars = languageDir.listFiles((dir, name) -> name.endsWith(".min.js"));
    assertNotNull(grammars, "Could not list " + languageDir);
    assertEquals(192, grammars.length,
        "Expected exactly the Highlight.js 11.7.0 grammar set");

    List<String> missing = new ArrayList<>();
    for (File grammar : grammars) {
      String language = grammar.getName().substring(0, grammar.getName().length() - ".min.js".length());
      if (highlighter.highlight("x", language) == null) {
        missing.add(language);
      }
    }

    assertEquals(List.of(), missing,
        missing.size() + " of " + grammars.length + " bundled grammars are not registered");
  }

  @Test
  void supportsCommonAliases() {
    assertNotNull(highlighter.highlight("echo hello", "sh"));
    assertNotNull(highlighter.highlight("$ ls -la", "shell"));
    assertNotNull(highlighter.highlight("template<class T> T f(T a);", "c++"));
    assertNotNull(highlighter.highlight("plain words", "text"));
  }

  @Test
  void plaintextIsNotHighlighted() {
    String result = highlighter.highlight("select the option and continue", "plaintext");

    assertNotNull(result);
    assertTrue(result.contains("select the option and continue"));
    assertFalse(result.contains("hljs-keyword"), "plaintext must not be given keyword spans");
  }

  @Test
  void returnsNullForUnknownOrMissingLanguage() {
    assertNull(highlighter.highlight("some words", "no-such-language"));
    assertNull(highlighter.highlight("some words", null));
    assertNull(highlighter.highlight("some words", ""));
  }

  @Test
  void escapesHtmlInHighlightedCode() {
    String result = highlighter.highlight("if (a < b && c > d) {}", "groovy");

    assertTrue(result.contains("&lt;"), result);
    assertTrue(result.contains("&gt;"), result);
    assertTrue(result.contains("&amp;&amp;"), result);
  }

  @Test
  void shipsHighlightJsLicenseAndBanner() throws IOException {
    try (InputStream license = getClass().getResourceAsStream("/highlightJs/LICENSE");
         InputStream bundle = getClass().getResourceAsStream("/highlightJs/highlight.js")) {
      assertNotNull(license, "The Highlight.js license must be shipped in the jar");
      assertNotNull(bundle, "The generated Highlight.js bundle must be shipped in the jar");
      assertTrue(new String(license.readAllBytes(), StandardCharsets.UTF_8)
          .contains("BSD 3-Clause License"));
      String banner = new String(bundle.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(banner.contains("Highlight.js v11.7.0"), banner);
    }
  }

  @Test
  void isUsableFromMultipleThreads() throws Exception {
    AtomicInteger failures = new AtomicInteger();
    Thread[] threads = new Thread[4];
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread(() -> {
        for (int n = 0; n < 25; n++) {
          try {
            if (!highlighter.highlight("def a = 1", "groovy").contains("hljs-keyword")) {
              failures.incrementAndGet();
            }
          } catch (RuntimeException e) {
            failures.incrementAndGet();
          }
        }
      });
    }
    for (Thread t : threads) { t.start(); }
    for (Thread t : threads) { t.join(); }

    assertEquals(0, failures.get(), "Concurrent highlighting failed " + failures.get() + " times");
  }
}
