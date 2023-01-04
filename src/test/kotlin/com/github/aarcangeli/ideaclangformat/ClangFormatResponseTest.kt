package com.github.aarcangeli.ideaclangformat

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class ClangFormatResponseTest {
  @Test
  fun marshal() {
    val value = ClangFormatResponse()
    for (i in 0..10) {
      val replacement = ClangFormatReplacement()
      replacement.offset = 23
      replacement.value = "asd $i"
      value.replacements.add(replacement)
    }
    val writeValueAsString = XmlMapper().writeValueAsString(value)
    println(writeValueAsString)
  }

  @Test
  fun unmarshal() {
    val stdout = """
      <?xml version='1.0'?>
      <replacements xml:space='preserve' incomplete_format='false'>
      <replacement offset='106' length='8'>    </replacement>
      </replacements>
      """.trimIndent()
    val response = ClangFormatResponse.unmarshal(stdout)
    assertEquals(false, response.incompleteFormat)
    assertEquals(1, response.replacements.size)
    assertEquals(106, response.replacements[0].offset)
    assertEquals(8, response.replacements[0].length)
    assertEquals("    ", response.replacements[0].value)
  }
}
