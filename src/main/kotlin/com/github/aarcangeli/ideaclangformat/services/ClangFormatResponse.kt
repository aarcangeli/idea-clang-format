package com.github.aarcangeli.ideaclangformat.services

import java.io.StringReader
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement
import javax.xml.bind.annotation.XmlValue

fun parseClangFormatResponse(stdout: String): ClangFormatResponse {
  val ctx: JAXBContext?
  try {
    ctx = JAXBContext.newInstance(ClangFormatResponse::class.java)
  }
  catch (e: JAXBException) {
    throw RuntimeException("Failed to load JAXB context", e)
  }

  val result = ctx.createUnmarshaller().unmarshal(StringReader(stdout))
  return result as ClangFormatResponse
}

@XmlRootElement(name = "replacements")
class ClangFormatResponse {
  @set:XmlElement(name = "replacement")
  var replacements: List<ClangFormatReplacement> = ArrayList()
}

class ClangFormatReplacement {
  @set:XmlAttribute
  var offset: Int = 0

  @set:XmlAttribute
  var length: Int = 0

  @set:XmlValue
  var value: String = ""
}
