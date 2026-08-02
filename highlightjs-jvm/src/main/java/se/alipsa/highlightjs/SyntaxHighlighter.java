package se.alipsa.highlightjs;

/** Highlights source code as HTML. */
public interface SyntaxHighlighter {

  /**
   * Highlights the source using the given language.
   *
   * @param source the code to highlight, never null
   * @param language a Highlight.js language name or alias
   * @return the highlighted HTML, or null if the language is null, blank or not
   *         supported. Callers should leave the code block untouched when null is
   *         returned; no language is ever guessed.
   */
  String highlight(String source, String language);
}
