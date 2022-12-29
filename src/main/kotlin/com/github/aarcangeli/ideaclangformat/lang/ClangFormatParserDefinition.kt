package com.github.aarcangeli.ideaclangformat.lang

import com.intellij.lang.PsiParser
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.yaml.YAMLParserDefinition

class ClangFormatParserDefinition : YAMLParserDefinition() {
  override fun getFileNodeType(): IFileElementType {
    return FILE
  }

  override fun createFile(viewProvider: FileViewProvider): PsiFile {
    return ClangFormatFileImpl(viewProvider)
  }

  override fun createParser(project: Project): PsiParser {
    return super.createParser(project)
  }

  companion object {
    private val FILE = IFileElementType(ClangFormatLanguage.INSTANCE)
  }
}
