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
    void indentedCodeBlocksAreAKnownGap() {
        String text = '''
```{groovy echo=false}
x = 5
```
    literal `= x` is expanded here
'''
        String processed = GmdTemplateEngine.processCodeBlocks(text)

        assertTrue(processed.contains('literal 5 is expanded here'),
            "Known gap: indented code blocks are not protected:\n$processed")
    }
}
