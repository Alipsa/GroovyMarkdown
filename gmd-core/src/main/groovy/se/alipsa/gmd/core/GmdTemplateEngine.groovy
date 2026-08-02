package se.alipsa.gmd.core

import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl

import javax.script.ScriptException
import java.util.regex.Matcher

class GmdTemplateEngine {


    /**
     * Appends a code block copied from the groovy code section e.g.
     * ```{groovy echo=TRUE}
     * // just some Groovy code
     * def x = 5
     * out.println('Hello World')
     * ```
     * becomes
     * ```{groovy echo=TRUE}
     * // just some Groovy code
     * def x = 5
     * out.println('Hello World')
     * ```
     * Hello World
     *
     * @param text the gmd text to process
     * @return the gmd text with code blocks "expanded"
    */
    static String processCodeBlocks(String text, Map bindings = [:]) throws GmdException {
        if (text == null) {
            throw new IllegalArgumentException("The gmd text cannot be null")
        }
        def classLoader = new GroovyClassLoader()
        def engine = new GroovyScriptEngineImpl(classLoader)
        String codeBlock = ''
        try (Printer out = new Printer()) {
            engine.put("out", out)
            bindings.each {
                engine.put(it.key, it.value)
            }
            boolean shouldBeProcessed = false
            boolean codeBlockStart = false
            boolean codeBlockEnd = false
            boolean echo = true
            Character plainFenceChar = null
            int plainFenceLength = 0
            String noSpaceLine
            StringBuilder codeBlockText = new StringBuilder()
            StringBuilder result = new StringBuilder()
            List<String> lines = text.readLines()
            int count = 0
            lines.each { line ->
                noSpaceLine = fenceCandidate(line)
                String legacyGroovyFence = line.trim()
                if (legacyGroovyFence.startsWith('```{groovy')
                        || (codeBlockStart && isClosingFence(legacyGroovyFence, '`', 3))) {
                    noSpaceLine = legacyGroovyFence
                }
                boolean startsPlainFence = noSpaceLine.startsWith('```') || noSpaceLine.startsWith('~~~')

                if (plainFenceChar == null && noSpaceLine.startsWith('```{groovy')) {
                    shouldBeProcessed = true
                    codeBlockStart = true
                    codeBlockEnd = false
                    // echo is a property of one block, not of the entire document.
                    echo = true
                    if (noSpaceLine.toLowerCase().contains("echo=false")) {
                        echo = false;
                    }
                } else if (codeBlockStart && isClosingFence(noSpaceLine, '`', 3)) {
                    codeBlockStart = false
                    codeBlockEnd = true
                } else if (!codeBlockStart && startsPlainFence) {
                    String marker = noSpaceLine.substring(0, 1)
                    int width = fenceRunLength(noSpaceLine, marker)
                    if (plainFenceChar == null) {
                        plainFenceChar = marker
                        plainFenceLength = width
                    } else if (plainFenceChar == marker && width >= plainFenceLength
                            && isClosingFence(noSpaceLine, marker, plainFenceLength)) {
                        plainFenceChar = null
                        plainFenceLength = 0
                    }
                }

                if (codeBlockStart) {
                    codeBlockText.append(line).append('\n')
                }

                if (codeBlockEnd) {
                    List<String> codeBlockCode = codeBlockText.readLines()
                    if (echo) {
                        codeBlockCode.set(0, '```groovy')
                        result.append(String.join('\n', codeBlockCode)).append('\n```\n')
                    }

                    codeBlockCode.remove(0)
                    codeBlock = String.join('\n', codeBlockCode)
                    //result.append("<%\n").append(codeBlock).append('\n%>\n')
                    // add an empty string to the end of the code block to not have the return value added to the result
                    //println("evaluating code block: $codeBlock")
                    engine.eval(codeBlock + '\n""')
                    def output = out.toString()
                    if (output.length() > 0) {
                        result.append(output)
                    }
                    out.clear()
                    codeBlockText.setLength(0)
                    codeBlockEnd = false
                } else if (!codeBlockStart) {
                    if (plainFenceChar == null && line.contains('`=')) {
                        shouldBeProcessed = true
                        result.append(expandInlineVars(line, engine))
                    } else {
                        result.append(line)
                    }
                    if (count < lines.size() - 1)
                        result.append('\n')
                }
                count++
            }
            if (codeBlockStart) {
                throw new GmdException('Unterminated Groovy code block')
            }
            if (shouldBeProcessed) {
                return result.toString()
            } else {
                return text
            }
        } catch(all) {
            String where = codeBlock.isEmpty() ? 'the gmd text' : "code block: $codeBlock"
            throw new GmdException("Failed to process $where", all)
        }
    }

    /**
     * Evaluate and replace all `= ` inline code blocks
     * the expression `= aVal ` is matched into two parts
     * one containing the full expression (`= aVal `) and the other
     * just the part to be evaluated (aVal )
     */
    static String expandInlineVars(String line, GroovyScriptEngineImpl engine) throws GmdException {
        String expression = ''
        String val = ''
        try {
            Matcher matcher = line =~ /`=(.+?)`/
            String newLine = line
            if (matcher.find()) {
                List<List<String>> matches = matcher.findAll()
                matches.each { expVal ->
                    expression = expVal.get(0)
                    val = expVal.get(1)
                    String evaluatedVal = String.valueOf(engine.eval(val))
                    newLine = newLine.replace(expression, evaluatedVal)
                }
                return newLine
            } else {
                return line
            }
        } catch (ScriptException | RuntimeException e) {
            throw new GmdException("Failed to expand inline variable (`=${val})", e)
        }
    }

    /** Remove at most the three spaces CommonMark permits before a fence. */
    private static String fenceCandidate(String line) {
        int indent = 0
        while (indent < line.length() && indent < 3 && line.charAt(indent) == ' ') {
            indent++
        }
        return line.substring(indent)
    }

    /** Length of the leading run of marker characters, i.e. the fence width. */
    private static int fenceRunLength(String line, String marker) {
        int i = 0
        while (i < line.length() && line.charAt(i) == marker.charAt(0)) {
            i++
        }
        return i
    }

    /** A closing fence has no info string or other non-whitespace suffix. */
    private static boolean isClosingFence(String line, String marker, int minimumWidth) {
        if (line.isEmpty() || line.charAt(0) != marker.charAt(0)) {
            return false
        }
        int width = fenceRunLength(line, marker)
        return width >= minimumWidth && line.substring(width).trim().isEmpty()
    }

    @Override
    String toString() {
        Package pkg = GmdTemplateEngine.package
        String version = pkg?.implementationVersion ?: 'unknown version'
        return "Groovy Markdown Processor, $version"
    }
}
