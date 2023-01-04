package com.github.aarcangeli.ideaclangformat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "replacements")
public class ClangFormatResponse {

  public static ClangFormatResponse unmarshal(@NotNull String stdout) {
    try {
      XmlMapper xmlMapper = new XmlMapper();
      return xmlMapper.readValue(stdout, ClangFormatResponse.class);
    }
    catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @JacksonXmlProperty(localName = "space", isAttribute = true)
  public String space;

  @JacksonXmlProperty(localName = "incomplete_format", isAttribute = true)
  public boolean incompleteFormat;

  @JacksonXmlProperty(localName = "replacement")
  @JacksonXmlElementWrapper(useWrapping = false)
  public final List<ClangFormatReplacement> replacements = new ArrayList<>();
}
