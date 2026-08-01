package test.alipsa.groovy.gmd

import org.apache.commons.lang3.StringUtils

import static org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import se.alipsa.gmd.core.GmdTemplateEngine

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
}
