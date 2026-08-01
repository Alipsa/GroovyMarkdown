# highlightjs-jvm

`highlightjs-jvm` exposes Highlight.js syntax highlighting to Java and Groovy applications without a browser or JavaFX. It uses a bundled ES5-transpiled Highlight.js 11.7.0 runtime evaluated by Nashorn 15.7. The checked-in runtime is the Babel-transpiled build artifact, so applications do not need Node.js or other native tooling at runtime.

The low-level API is:

```java
SyntaxHighlighter highlighter = new HighlightJsHighlighter();
String html = highlighter.highlight("def answer = 42", "groovy");
String detected = highlighter.highlightAuto(source);
```

For HTML fragments, `HtmlSyntaxHighlighter` finds direct `pre > code` blocks, preserves `nohighlight`, and inserts the returned `hljs-*` spans. The default engine is initialized once and synchronized because Nashorn engines are not thread-safe.
