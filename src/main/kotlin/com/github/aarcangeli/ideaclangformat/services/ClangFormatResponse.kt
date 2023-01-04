package com.github.aarcangeli.ideaclangformat.services

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import org.simpleframework.xml.Text
import org.simpleframework.xml.core.Persister

fun parseClangFormatResponse(stdout: String): ClangFormatResponse {
  val serializer = Persister()
  return serializer.read(ClangFormatResponse::class.java, stdout)
}

@Root(name = "replacements", strict = false)
class ClangFormatResponse {
  @field:ElementList(name = "replacement", required = false, inline = true)
  var replacements: List<ClangFormatReplacement> = ArrayList()
}

@Root(strict = false, name = "replacement")
class ClangFormatReplacement {
  @field:Attribute(required = true)
  var offset: Int = 0

  @field:Attribute(required = true)
  var length: Int = 0

  @field:Text(required = false)
  var value: String = ""
}
