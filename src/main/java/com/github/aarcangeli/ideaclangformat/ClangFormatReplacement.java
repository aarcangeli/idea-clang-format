package com.github.aarcangeli.ideaclangformat;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

public class ClangFormatReplacement {
  @JacksonXmlProperty(isAttribute = true)
  public int offset;

  @JacksonXmlProperty(isAttribute = true)
  public int length;

  @JacksonXmlText(false)
  public String value;
}
