package test.alipsa.groovy.gmd

import org.apache.commons.lang3.StringUtils

import static org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import se.alipsa.gmd.core.GmdTemplateEngine
import se.alipsa.gmd.core.GmdException

class GmdTemplateEngineTest {

    @Test
    void testEcho() {
        String text = """
Before
```{groovy echo=TRUE}
    // just some Groovy code
    def x = 5
    out.println('Hello World')
```
After code block"""
        String processed = GmdTemplateEngine.processCodeBlocks(text)
        String expected = """
Before
```groovy
    // just some Groovy code
    def x = 5
    out.println('Hello World')
```
Hello World
After code block"""

        if (!expected.equals(processed)) {
            println("Difference is: " + StringUtils.difference(expected, processed)
                + ", at index: " + StringUtils.indexOfDifference(expected, processed))
        }
        assertEquals(expected, processed)
    }

    @Test
    void testCodeOnly() {
        String text = """
            Before
            ```{groovy echo=false}
            // just some Groovy code
            def x = 5
            out.println('Hello World')  
            ```
            After"""

        assertEquals("""
            Before
Hello World
            After""", GmdTemplateEngine.processCodeBlocks(text))
    }

    @Test
    void testNoEcho() {
        String text = """
            Before
            ```{groovy}
            // just some Groovy code
            def x = 5
            out.println('Hello World')  
            ```
            After
        """

        assertEquals("""
            Before
```groovy
// just some Groovy code
def x = 5
out.println('Hello World')  
```
Hello World
            After
        """, GmdTemplateEngine.processCodeBlocks(text))
    }

    @Test
    void anIndentedGroovyFenceDedentsItsBody() {
        String text = '''
Example:

    ```{groovy}
    out.println('EXECUTED')
    ```

end
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains("```groovy\nout.println('EXECUTED')\n```"),
            "The echoed body must line up with the fence that wraps it:\n$processed")
        assertTrue(processed.contains('EXECUTED\n'), processed)
    }

    @Test
    void dedentOnlyRemovesTheFenceIndent() {
        String text = '''
    ```{groovy}
        out.println('deep')
    ```
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains("```groovy\n    out.println('deep')\n```"),
            "Indentation beyond the fence indent must be preserved:\n$processed")
    }

    @Test
    void aColumnZeroFenceLeavesItsBodyAlone() {
        String text = '''
```{groovy}
    out.println('indented body')
```
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains("```groovy\n    out.println('indented body')\n```"),
            "A fence at column 0 dedents by 0:\n$processed")
    }

    @Test
    void dedentAppliesToTheEvaluatedSource() {
        String text = '''
    ```{groovy echo=false}
    out.print("""a
  b""")
    ```
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('a\nb'),
            "The dedent applies to the evaluated source too:\n$processed")
    }

    @Test
    void unterminatedCodeBlockOutputDoesNotSwallowTheNextLine() {
        String text = "```{groovy echo=false}\nout.print('X')\n```\n# Heading\n"

        assertEquals("X\n# Heading\n", GmdTemplateEngine.processCodeBlocks(text))

        text = "```{groovy echo=false}\nout.println('X')\n```\n# Heading\n"

        assertEquals("X\n# Heading\n", GmdTemplateEngine.processCodeBlocks(text))
    }

    @Test
    void inlineOutPrintIsTerminatedBeforeTheNextLine() {
        String text = "```{groovy echo=false}\nout.print('Hello ')\n```\nworld\n"

        assertEquals("Hello \nworld\n", GmdTemplateEngine.processCodeBlocks(text))
    }

    @Test
    void testInlineVars() {
        def text = """
        ```{groovy echo=false}
            aVal = 123 + 234
        ```
        123 + 234 = `= aVal `
        """
        assertEquals("""
        123 + 234 = 357
        """, GmdTemplateEngine.processCodeBlocks(text))

        text = """
        ```{groovy echo=false}
        x = 5
        ```
        X = `= x`
        
        """.stripIndent()

        assertEquals("""
        X = 5

        """.stripIndent(), GmdTemplateEngine.processCodeBlocks(text))

    }

    @Test
    void echoIsResetForEachCodeBlock() {
        String text = '''
```{groovy echo=false}
out.println('hidden source')
```
```{groovy}
out.println('visible source')
```
'''

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertFalse(processed.contains("```groovy\nout.println('hidden source')"))
        assertTrue(processed.contains("```groovy\nout.println('visible source')"))
    }

    @Test
    void unterminatedCodeBlockIsRejected() {
        assertThrows(Exception.class) {
            GmdTemplateEngine.processCodeBlocks('''
```{groovy echo=false}
out.println('missing terminator')
''')
        }
    }

    @Test
    void inlineExpressionsInsideBacktickFencesAreLeftAlone() {
        String text = '''
```{groovy echo=false}
x = 5
```
Value is `= x`
```text
literal `= 1+1` stays put
```
Done
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('Value is 5'), "Real inline vars must still expand:\n$processed")
        assertTrue(processed.contains('literal `= 1+1` stays put'),
            "Fenced content must be verbatim:\n$processed")
    }

    @Test
    void inlineExpressionsInsideTildeFencesAreLeftAlone() {
        String text = '''
```{groovy echo=false}
x = 5
```
Value is `= x`
~~~text
literal `= 1+1` stays put
~~~
Done
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('Value is 5'), processed)
        assertTrue(processed.contains('literal `= 1+1` stays put'),
            "Tilde-fenced content must be verbatim:\n$processed")
    }

    @Test
    void aFenceIsOnlyClosedByItsOwnDelimiter() {
        String text = '''
```{groovy echo=false}
x = 5
```
~~~text
```
literal `= 1+1` stays put
```
~~~
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('literal `= 1+1` stays put'),
            "A backtick line must not close a tilde fence:\n$processed")
    }

    @Test
    void aLongerFenceIsNotClosedByAShorterOne() {
        String text = '''
```{groovy echo=false}
x = 5
```
````markdown
```{groovy}
y = 1
```
inline `= x` shown as source
````
Done
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('inline `= x` shown as source'),
            "A three-backtick line must not close a four-backtick fence:\n$processed")
    }

    @Test
    void aFenceIsClosedByAnEqualOrLongerRun() {
        String text = '''
```{groovy echo=false}
x = 5
```
```text
verbatim
````
value `= x` after the fence closed
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('value 5 after the fence closed'),
            "A four-backtick line should close a three-backtick fence:\n$processed")
    }

    @Test
    void onlyAValidClosingFenceEndsTheBlock() {
        String text = '''
```text
    ```
```not-a-closing-fence
literal `= 1+1` stays put
```
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('literal `= 1+1` stays put'),
            "Indented or annotated fence-like lines must remain content:\n$processed")
    }

    @Test
    void plainFencesAreNotTreatedAsGroovyBlocks() {
        String text = '''
```java
int x = 5;
```
After
'''
        assertEquals(text, GmdTemplateEngine.processCodeBlocks(text))
    }

    @Test
    void indentedCodeBlocksAreLeftAlone() {
        String text = '''
```{groovy echo=false}
x = 5
```
    literal `= x ` is not expanded here
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('literal `= x ` is not expanded here'),
            "An indented code block is literal:\n$processed")
        assertFalse(processed.contains('literal 5'), processed)
    }

    @Test
    void indentIsRelativeToTheDocumentBaseIndent() {
        String text = """
        ```{groovy echo=false}
        x = 5
        ```
        expanded `= x ` here

            literal `= x ` here
        """

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('expanded 5 here'), processed)
        assertTrue(processed.contains('literal `= x ` here'), processed)
    }

    @Test
    void anIndentedLineContinuingAParagraphIsStillExpanded() {
        String text = '''
```{groovy echo=false}
x = 5
```
Some prose
    continues here with `= x `
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('continues here with 5'), processed)
    }

    @Test
    void indentedCodeAfterLeafBlocksIsLeftAlone() {
        ['# Heading', 'Title\n=====', '---'].each { leafBlock ->
            String text = "```{groovy echo=false}\nx = 5\n```\n${leafBlock}\n    literal `= x ` here\n"

            String processed = GmdTemplateEngine.processCodeBlocks(text)

            assertTrue(processed.contains('literal `= x ` here'), processed)
            assertFalse(processed.contains('literal 5'), processed)
        }
    }

    @Test
    void indentedCodeInsideABlockQuoteIsLeftAlone() {
        String text = '''
```{groovy echo=false}
x = 5
```
>     literal `= x ` here
'''

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('literal `= x ` here'), processed)
        assertFalse(processed.contains('literal 5'), processed)
    }

    @Test
    void anEmptyEchoFalseBlockPreservesThePreviousParagraph() {
        String text = '''
prose line
```{groovy echo=false}
x = 5
```
    continued `= x ` here
'''

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('continued 5 here'), processed)
    }

    @Test
    void aBlankLineDoesNotEndAnIndentedCodeBlock() {
        String text = '''
```{groovy echo=false}
x = 5
```
    first `= x ` literal

    second `= x ` literal
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('first `= x ` literal'), processed)
        assertTrue(processed.contains('second `= x ` literal'), processed)
    }

    @Test
    void aTabIndentedLineIsNotTreatedAsACodeBlock() {
        String text = "```{groovy echo=false}\nx = 5\n```\n\tliteral `= x ` here\n"

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('literal 5 here'),
            "Known limitation: tabs are not counted as indentation:\n$processed")
    }

    @Test
    void crlfLinesAreClassifiedCorrectly() {
        String text = "```{groovy echo=false}\r\nx = 5\r\n```\r\n    literal `= x ` here\r\n   \r\n"

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('literal `= x ` here'), processed)
    }

    @Test
    void baseIndentIgnoresCodeBlockBodies() {
        String text = """
        ```{groovy echo=false}
        x = 5
        multi = '''line one
line two'''
        ```
        Value `= x `
        """

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('Value 5'),
            "A column-0 line inside a code block must not change the document's base indent:\n$processed")
    }

    @Test
    void aStrayColumnZeroLineDoesNotCollapseTheBaseIndent() {
        String text = """
        ```{groovy echo=false}
        x = 5
        ```
<table><tr><td>html at column 0</td></tr></table>
        expanded `= x ` here
        """

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('expanded 5 here'),
            "A column-0 line must not collapse the base indent:\n$processed")
    }

    @Test
    void aListItemContinuationStillExpands() {
        String text = '''
```{groovy echo=false}
x = 5
```
1. item

    continued `= x `
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('continued 5'), processed)
    }

    @Test
    void indentedCodeAfterAListIsLeftAlone() {
        String text = '''
```{groovy echo=false}
x = 5
```

- item

text

    literal `= x ` here
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('literal `= x ` here'),
            "An indented code block after an ended list is literal:\n$processed")
        assertFalse(processed.contains('literal 5'), processed)
    }

    @Test
    void echoFalseWithSpacesIsHonoured() {
        String text = '''
``` {groovy echo = false}
x = 5
```
Value `= x`
'''

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertFalse(processed.contains('```groovy'), processed)
        assertTrue(processed.contains('Value 5'), processed)
    }

    @Test
    void groovyFenceWithSpaceBeforeBraceIsExecuted() {
        String text = '''
``` {groovy}
x = 5
```
Value `= x`
'''

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('```groovy'), processed)
        assertTrue(processed.contains('Value 5'), processed)
    }

    @Test
    void unterminatedPlainFenceIsRejected() {
        assertThrows(Exception.class) {
            GmdTemplateEngine.processCodeBlocks('''
```text
unclosed
''')
        }
    }

    @Test
    void aCodeSpanOnTheSameLineDoesNotCorruptTheMatch() {
        String text = 'use `=` and `foo` in code'
        String processed = GmdTemplateEngine.processCodeBlocks(text)
        assertEquals(text, processed)
    }

    @Test
    void repeatedIdenticalExpressionsEachEvaluateAndSubstituteIndependently() {
        String text = '''
        ```{groovy echo=false}
        c = 0
        ```
        x `= c++ ` y `= c++ ` z
        '''.stripIndent()

        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('x 0 y 1 z'), processed)
    }

    @Test
    void unterminatedCodeBlockMessageIsNotWrapped() {
        GmdException e = assertThrows(GmdException.class) {
            GmdTemplateEngine.processCodeBlocks('''
```{groovy echo=false}
out.println('missing terminator')
''')
        }
        assertEquals('Unterminated Groovy code block', e.getMessage())
    }

    @Test
    void aUserSuppliedOutBindingDoesNotClobberCodeBlockOutput() {
        String text = '''
```{groovy}
out.println('x')
```
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text, [out: 'clobbered'])

        assertTrue(processed.contains('x'), processed)
    }

    @Test
    void trailingNewlineIsPreservedWhenPresent() {
        String text = "# Title\n\nHello `= 1+1 `\n"
        String processed = GmdTemplateEngine.processCodeBlocks(text)
        assertEquals("# Title\n\nHello 2\n", processed)
    }

    @Test
    void trailingNewlineIsNotAddedWhenAbsent() {
        String text = "# Title\n\nHello `= 1+1 `"
        String processed = GmdTemplateEngine.processCodeBlocks(text)
        assertEquals("# Title\n\nHello 2", processed)
    }
}
