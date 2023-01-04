package com.github.aarcangeli.ideaclangformat

import com.github.aarcangeli.ideaclangformat.services.ClangFormatReplacement
import com.github.aarcangeli.ideaclangformat.services.ClangFormatResponse
import com.github.aarcangeli.ideaclangformat.services.parseClangFormatResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.simpleframework.xml.core.Persister
import java.io.StringWriter

class ClangFormatResponseTest {
  @Test
  fun marshal() {
    val response = ClangFormatResponse()
    val replacement = ClangFormatReplacement()
    replacement.offset = 1
    replacement.length = 2
    replacement.value = "value"
    response.replacements = arrayListOf(replacement)
    val expectedValue = """
      <replacements>
         <replacement offset="1" length="2">value</replacement>
      </replacements>
      """.trimIndent()
    assertEquals(
      expectedValue,
      formatXml(response)
    )
  }

  @Test
  fun unmarshal() {
    val stdout = """
      <?xml version='1.0'?>
      <replacements xml:space='preserve' incomplete_format='false'>
      <replacement offset='106' length='8'>&#10;&#10;    </replacement>
      </replacements>
      """.trimIndent()
    val response = parseClangFormatResponse(stdout)
    assertEquals(1, response.replacements.size)
    assertEquals(106, response.replacements[0].offset)
    assertEquals(8, response.replacements[0].length)
    assertEquals("\n\n    ", response.replacements[0].value)
  }

  @Test
  fun unmarshalEmptyReplacements() {
    val stdout = """
        <?xml version='1.0'?>
        <replacements xml:space='preserve' incomplete_format='false'>
        </replacements>
      """.trimIndent()
    val response = parseClangFormatResponse(stdout)
    assertEquals(0, response.replacements.size)
  }

  @Test
  fun unmarshalEmptyValue() {
    val stdout = """
      <?xml version='1.0'?>
      <replacements xml:space='preserve' incomplete_format='false'>
      <replacement offset='0' length='12'></replacement>
      </replacements>
      """.trimIndent()
    val response = parseClangFormatResponse(stdout)
    assertEquals(1, response.replacements.size)
    assertEquals(0, response.replacements[0].offset)
    assertEquals(12, response.replacements[0].length)
    assertEquals("", response.replacements[0].value)
  }

  private fun formatXml(response: ClangFormatResponse): String {
    val writer = StringWriter()
    Persister().write(response, writer)
    return writer.buffer.toString()
  }
}
