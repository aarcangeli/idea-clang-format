package com.github.aarcangeli.ideaclangformat.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

class ClangFormatFileType private constructor() : LanguageFileType(ClangFormatLanguage.INSTANCE) {
  override fun getName(): @NonNls String {
    return "ClangFormatStyle"
  }

  override fun getDescription(): String {
    return "Clang-format style options"
  }

  override fun getDefaultExtension(): String {
    return "clang-format"
  }

  override fun getIcon(): Icon {
    // Reuse the same icon as the .editorconfig file
    return AllIcons.Nodes.Editorconfig
  }

  companion object {
    val INSTANCE = ClangFormatFileType()
  }
}
