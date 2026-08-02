package se.alipsa.highlightjs;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Highlight.js running on Rhino.
 *
 * The bundle is a syntax-only transpile of the upstream distribution; Rhino
 * supplies Symbol, Map, Set and Object.assign natively so nothing is polyfilled.
 * One scope is shared, and Highlight.js keeps mutable state in it, so calls are
 * synchronized - concurrent use corrupts that state.
 */
public final class HighlightJsHighlighter implements SyntaxHighlighter {

  private static final String RESOURCE = "/highlightJs/highlight.js";
  private static final String FUNCTION_NAME = "javaHighlight";

  private final Scriptable scope;

  public HighlightJsHighlighter() {
    Context cx = enterContext();
    try (InputStream input = HighlightJsHighlighter.class.getResourceAsStream(RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("Missing Highlight.js resource " + RESOURCE);
      }
      Scriptable created = cx.initStandardObjects();
      try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
        cx.evaluateReader(created, reader, "highlight.js", 1, null);
      }
      if (!(created.get(FUNCTION_NAME, created) instanceof Function)) {
        throw new IllegalStateException("The Highlight.js bundle does not define " + FUNCTION_NAME);
      }
      scope = created;
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("Could not initialize the Highlight.js Rhino engine", e);
    } finally {
      Context.exit();
    }
  }

  @Override
  public synchronized String highlight(String source, String language) {
    if (source == null) {
      throw new IllegalArgumentException("Source code cannot be null");
    }
    if (language == null || language.isBlank()) {
      return null;
    }
    Context cx = enterContext();
    try {
      Function function = (Function) scope.get(FUNCTION_NAME, scope);
      Object result = function.call(cx, scope, scope, new Object[]{source, language});
      if (result == null || Undefined.isUndefined(result)) {
        return null;
      }
      return Context.toString(result);
    } finally {
      Context.exit();
    }
  }

  private static Context enterContext() {
    Context cx = Context.enter();
    // Must be set before initStandardObjects for Symbol/Map/Set to be present.
    cx.setLanguageVersion(Context.VERSION_ES6);
    // Interpreted mode avoids Rhino's 64K per-method bytecode limit on this
    // ~1.2 MB script.
    cx.setInterpretedMode(true);
    return cx;
  }
}
