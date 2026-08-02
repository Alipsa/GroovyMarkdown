# Highlight.js JVM release history

## v3.1.0, unreleased
- add the independent `highlightjs-jvm` module
- expose Highlight.js string highlighting through the `SyntaxHighlighter` API
- add Jsoup-based HTML code-block highlighting with `HtmlSyntaxHighlighter`
- run Highlight.js 11.7.0 on Rhino 1.9.1 with all 192 languages
- generate the bundle reproducibly from the upstream distribution with buildBundle.sh;
  syntax-only transpile, no polyfills, no hand-editing
- return null instead of auto-detecting when a language is unknown or absent
- synchronize access because Highlight.js keeps mutable state in the shared scope
