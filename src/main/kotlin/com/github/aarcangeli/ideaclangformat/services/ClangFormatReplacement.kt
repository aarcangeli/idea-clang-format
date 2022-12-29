package com.github.aarcangeli.ideaclangformat.services

import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlValue

class ClangFormatReplacement {
  @XmlAttribute
  var offset = 0

  @XmlAttribute
  var length = 0

  @XmlValue
  var value: String? = null
}
