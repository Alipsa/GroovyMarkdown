# Highlight.js JVM release history

## v3.0.3, unreleased
- add the independent `highlightjs-jvm` module
- expose Highlight.js string highlighting through the `SyntaxHighlighter` API
- add Jsoup-based HTML code-block highlighting with `HtmlSyntaxHighlighter`
- bundle an ES5-transpiled Highlight.js 11.7.0 runtime for Nashorn 15.7
- synchronize access to the Nashorn engine because it is not thread-safe
