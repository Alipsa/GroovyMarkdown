/*
 * The Java-facing entry point.
 *
 * Returning null means "leave this block alone": either no language was given,
 * or it is one Highlight.js does not know. Auto-detection is deliberately never
 * used - it mislabels prose and console output as code.
 */
function javaHighlight(code, language) {
  if (!language) {
    return null;
  }
  if (!hljs.getLanguage(language)) {
    return null;
  }
  return hljs.highlight(code, { language: language, ignoreIllegals: true }).value;
}
