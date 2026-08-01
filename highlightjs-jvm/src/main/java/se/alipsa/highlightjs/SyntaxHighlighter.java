package se.alipsa.highlightjs;

/** Converts source code into Highlight.js HTML. */
public interface SyntaxHighlighter {

  String highlight(String source, String language);

  String highlightAuto(String source);
}
