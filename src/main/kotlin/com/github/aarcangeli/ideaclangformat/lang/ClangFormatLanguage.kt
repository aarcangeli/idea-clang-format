package com.github.aarcangeli.ideaclangformat.lang

import com.intellij.lang.Language
import org.jetbrains.yaml.YAMLLanguage

class ClangFormatLanguage : Language(YAMLLanguage.INSTANCE, "aarcangeli.ClangFormat") {
  override fun getDisplayName(): String {
    return "Clang-Format Style Options"
  }

  companion object {
    @JvmField
    val INSTANCE = ClangFormatLanguage()
  }
}
