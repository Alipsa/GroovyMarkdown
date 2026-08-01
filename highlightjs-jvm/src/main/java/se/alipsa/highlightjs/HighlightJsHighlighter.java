package se.alipsa.highlightjs;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Highlight.js implementation backed by a single initialized Nashorn engine.
 * Nashorn is not thread-safe, therefore invocations are synchronized.
 */
public final class HighlightJsHighlighter implements SyntaxHighlighter {

  private static final String RESOURCE = "/highlightJs/highlight-es5.js";
  private static final String FUNCTION_NAME = "javaHighlight";

  private final Invocable invocable;

  public HighlightJsHighlighter() {
    try {
      ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
      try (InputStream input = HighlightJsHighlighter.class.getResourceAsStream(RESOURCE)) {
        if (input == null) {
          throw new IllegalStateException("Missing Highlight.js resource " + RESOURCE);
        }
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
          engine.eval(reader);
        }
      }
      invocable = (Invocable) engine;
    } catch (IOException | ScriptException | RuntimeException e) {
      throw new IllegalStateException("Could not initialize the Highlight.js Nashorn engine", e);
    }
  }

  @Override
  public synchronized String highlight(String source, String language) {
    return invoke(source, language);
  }

  @Override
  public synchronized String highlightAuto(String source) {
    return invoke(source, null);
  }

  private String invoke(String source, String language) {
    if (source == null) {
      throw new IllegalArgumentException("Source code cannot be null");
    }
    try {
      Object result = invocable.invokeFunction(FUNCTION_NAME, source, language);
      return result == null ? "" : result.toString();
    } catch (ScriptException | NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "Could not highlight " + (language == null ? "source" : language) + " code", e);
    }
  }
}
