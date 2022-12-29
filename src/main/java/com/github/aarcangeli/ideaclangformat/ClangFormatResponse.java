package com.github.aarcangeli.ideaclangformat;

import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.StringReader;
import java.util.List;

@XmlRootElement(name = "replacements")
public class ClangFormatResponse {
  static final JAXBContext REPLACEMENTS_CTX;

  static {
    try {
      REPLACEMENTS_CTX = JAXBContext.newInstance(ClangFormatResponse.class);
    }
    catch (JAXBException e) {
      throw new RuntimeException("Failed to load JAXB context", e);
    }
  }

  public static ClangFormatResponse unmarshal(@NotNull @NlsSafe String stdout) {
    try {
      // JAXB closes the InputStream.
      return (ClangFormatResponse) REPLACEMENTS_CTX.createUnmarshaller().unmarshal(new StringReader(stdout));
    }
    catch (JAXBException e) {
      throw new ClangFormatError("Failed to parse clang-format XML replacements\n" + stdout, e);
    }
  }

  @XmlElement(name = "replacement")
  public List<ClangFormatReplacement> replacements;
}

