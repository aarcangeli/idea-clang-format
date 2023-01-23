package com.github.aarcangeli.ideaclangformat

import com.github.aarcangeli.ideaclangformat.services.parseClangFormatResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class ClangFormatResponseTest {
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
  fun unmarshal2() {
    val stdout = """
      <?xml version='1.0'?>
      <replacements xml:space='preserve' incomplete_format='false'>
      <replacement offset='29' length='3'>&#10;&#10;</replacement>
      </replacements>
      """.trimIndent()
    val response = parseClangFormatResponse(stdout)
    assertEquals(1, response.replacements.size)
    assertEquals(29, response.replacements[0].offset)
    assertEquals(3, response.replacements[0].length)
    assertEquals("\n\n", response.replacements[0].value)
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
}
