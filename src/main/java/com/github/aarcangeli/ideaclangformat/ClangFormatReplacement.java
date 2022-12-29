package com.github.aarcangeli.ideaclangformat;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

public class ClangFormatReplacement {
  @XmlAttribute
  public int offset;
  @XmlAttribute
  public int length;
  @XmlValue
  public String value;
}
