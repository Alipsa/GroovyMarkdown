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
     * Indentation is lenient by design: a ```{groovy} fence is recognised at any
     * indent, so a .gmd document written inside an indented Groovy string still
     * works. The body is dedented by the fence's own indent so the two line up.
     * The consequence is that an indented block cannot be used to *show* GMD
     * syntax without running it - wrap such an example in a longer plain fence
     * (````) instead. Only spaces are counted; a tab-indented fence is treated
     * as column 0. The echoed fence is always emitted at column 0, so a Groovy
     * block written inside a list item does not stay inside that item - the
     * emitted block ends the list and the next item starts a new one.
     * For inline-variable expansion, the document base is the first non-blank
     * line's indent. That is an invariant, not a global minimum: if the first
     * line is shallower than a body indented four or more spaces beyond it, the
     * body is treated as literal and does not expand. Put the first non-blank
     * line at the document's own indent.
     * @param text the gmd text to process
     * @return the gmd text with code blocks "expanded"
     */
    static String processCodeBlocks(String text, Map bindings = [:]) throws GmdException {
        if (text == null) {
            throw new IllegalArgumentException("The gmd text cannot be null")
        }
        String codeBlock = ''
        try (GroovyClassLoader classLoader = new GroovyClassLoader(); Printer out = new Printer()) {
            def engine = new GroovyScriptEngineImpl(classLoader)
            bindings.each {
                engine.put(it.key, it.value)
            }
            // out is reserved for capturing code block output; bind it last so it
            // always wins over a caller-supplied binding of the same name.
            engine.put("out", out)
            boolean shouldBeProcessed = false
            boolean codeBlockStart = false
            boolean codeBlockEnd = false
            boolean echo = true
            Character plainFenceChar = null
            int plainFenceLength = 0
            int codeBlockIndent = 0
            boolean inIndentedCode = false
            boolean previousLineWasParagraph = false
            boolean previousLineWasBlockQuote = false
            int listContentColumn = -1
            String noSpaceLine
            StringBuilder codeBlockText = new StringBuilder()
            StringBuilder result = new StringBuilder()
            boolean endsWithNewline = text.endsWith('\n')
            List<String> lines = text.readLines()
            int documentIndent = baseIndent(lines)
            int count = 0
            lines.each { line ->
                noSpaceLine = fenceCandidate(line)
                String legacyGroovyFence = line.trim()
                String infoString = legacyGroovyFence.replace(' ', '')
                if (infoString.startsWith('```{groovy')) {
                    noSpaceLine = infoString
                } else if (codeBlockStart && isClosingFence(legacyGroovyFence, '`', 3)) {
                    noSpaceLine = legacyGroovyFence
                }
                boolean startsPlainFence = noSpaceLine.startsWith('```') || noSpaceLine.startsWith('~~~')

                if (plainFenceChar == null && noSpaceLine.startsWith('```{groovy')) {
                    shouldBeProcessed = true
                    codeBlockStart = true
                    codeBlockEnd = false
                    codeBlockIndent = leadingSpaces(line)
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
                    codeBlockText.append(dedent(line, codeBlockIndent)).append('\n')
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
                    boolean emittedMarkdown = echo
                    if (output.length() > 0) {
                        result.append(output)
                        if (!output.endsWith('\n')) {
                            result.append('\n')
                        }
                        emittedMarkdown = true
                    }
                    out.clear()
                    codeBlockText.setLength(0)
                    codeBlockEnd = false
                    if (emittedMarkdown) {
                        inIndentedCode = false
                        previousLineWasParagraph = false
                        previousLineWasBlockQuote = false
                        listContentColumn = -1
                    }
                } else if (!codeBlockStart) {
                    if (plainFenceChar != null || startsPlainFence) {
                        inIndentedCode = false
                        previousLineWasParagraph = false
                    } else if (line.isBlank()) {
                        previousLineWasParagraph = false
                        previousLineWasBlockQuote = false
                    } else {
                        String contentLine = withoutDocumentAndBlockQuotePrefixes(line, documentIndent)
                        boolean isBlockQuote = contentLine != withoutDocumentIndent(line, documentIndent)
                        if (contentLine.isBlank()) {
                            // A block-quote line with no content (">" or "> ") is a blank
                            // line in CommonMark; classify it as one so a following
                            // indented line still counts as a code block, not a
                            // paragraph continuation.
                            previousLineWasParagraph = false
                            previousLineWasBlockQuote = isBlockQuote
                        } else {
                            if (isBlockQuote && !previousLineWasBlockQuote) {
                                previousLineWasParagraph = false
                                listContentColumn = -1
                            }
                            int relativeIndent = leadingSpaces(contentLine)
                            def listMarker = contentLine =~ /^\s*([-*+]|\d+[.)])\s+/
                            boolean isLeafBlock = isNonParagraphLeafBlock(contentLine)
                            if (isLeafBlock) {
                                // CommonMark gives thematic breaks precedence over list markers
                                // such as the leading "* " in "* * *".
                                inIndentedCode = false
                                // A thematic break dedented past the list content column ends
                                // the list; one aligned with it (like "  --- " in a list item)
                                // keeps the list context for the lines that follow.
                                if (listContentColumn >= 0 && relativeIndent < listContentColumn) {
                                    listContentColumn = -1
                                }
                            } else if (relativeIndent < 4 && listMarker.find()) {
                                listContentColumn = listMarker.end()
                                inIndentedCode = false
                            } else {
                                if (listContentColumn >= 0 && relativeIndent < listContentColumn) {
                                    listContentColumn = -1
                                }
                                if (listContentColumn >= 0 && relativeIndent < listContentColumn + 4) {
                                    inIndentedCode = false
                                } else {
                                    if (relativeIndent < 4) {
                                        inIndentedCode = false
                                    } else if (!previousLineWasParagraph) {
                                        inIndentedCode = true
                                    }
                                }
                            }
                            previousLineWasParagraph = !inIndentedCode && !isLeafBlock
                            previousLineWasBlockQuote = isBlockQuote
                        }
                    }
                    if (plainFenceChar == null && !inIndentedCode && line.contains('`=')) {
                        shouldBeProcessed = true
                        result.append(expandInlineVars(line, engine))
                    } else {
                        result.append(line)
                    }
                    if (count < lines.size() - 1 || endsWithNewline)
                        result.append('\n')
                }
                count++
            }
            if (codeBlockStart) {
                throw new GmdException('Unterminated Groovy code block')
            }
            if (plainFenceChar != null) {
                throw new GmdException('Unterminated plain code fence')
            }
            if (shouldBeProcessed) {
                return result.toString()
            } else {
                return text
            }
        } catch (GmdException e) {
            throw e
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
        String val = ''
        try {
            Matcher matcher = line =~ /`=([^`]+)`/
            StringBuilder newLine = new StringBuilder()
            while (matcher.find()) {
                val = matcher.group(1)
                String evaluatedVal = String.valueOf(engine.eval(val))
                matcher.appendReplacement(newLine, Matcher.quoteReplacement(evaluatedVal))
            }
            matcher.appendTail(newLine)
            return newLine.toString()
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

    /** Number of leading space characters. Tabs are not counted; see processCodeBlocks. */
    private static int leadingSpaces(String line) {
        int i = 0
        while (i < line.length() && line.charAt(i) == ' ') {
            i++
        }
        return i
    }

    /** Remove the document's base indent without treating a shallower line as negative. */
    private static String withoutDocumentIndent(String line, int documentIndent) {
        return line.substring(Math.min(documentIndent, leadingSpaces(line)))
    }

    /**
     * Remove block-quote container markers before measuring content indentation.
     * A block quote may itself be indented by the document's base indent.
     */
    private static String withoutDocumentAndBlockQuotePrefixes(String line, int documentIndent) {
        String content = withoutDocumentIndent(line, documentIndent)
        while (true) {
            Matcher marker = content =~ /^ {0,3}>[ \t]?/
            if (!marker.find()) {
                return content
            }
            content = content.substring(marker.end())
        }
    }

    /** Headings and rules are leaf blocks, so following indented lines start code blocks. */
    private static boolean isNonParagraphLeafBlock(String line) {
        if (line ==~ /^ {0,3}#{1,6}(?:[ \t]+.*)?$/ || line ==~ /^ {0,3}[=-]+[ \t]*$/) {
            return true
        }
        String marker = line.trim().replaceAll(/[ \t]/, '')
        return marker.length() >= 3 && (marker ==~ /\*+/ || marker ==~ /_+/ || marker ==~ /-+/)
    }

    /**
     * The indent the document sits at: the indent of its first non-blank line.
     * Indented code blocks are recognised relative to this, so a .gmd written
     * inside an indented Groovy string behaves the same as one written at
     * column 0.
     *
     * Anchored on the first line deliberately: a global minimum could be
     * collapsed by a column-0 line inside a code block or a stray column-0
     * prose line (HTML block, table row), reclassifying the whole document.
     * The first line is therefore the invariant: when it is shallower than
     * the body, body lines four or more spaces beyond it are literal. Authors
     * must start a document at its own indent.
     */
    private static int baseIndent(List<String> lines) {
        for (String line in lines) {
            if (!line.isBlank()) {
                return leadingSpaces(line)
            }
        }
        return 0
    }

    /** Removes up to {@code width} leading spaces. Shorter indents are left untouched. */
    private static String dedent(String line, int width) {
        int i = 0
        while (i < width && i < line.length() && line.charAt(i) == ' ') {
            i++
        }
        return line.substring(i)
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
