package com.github.aarcangeli.ideaclangformat

import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.ExternalFormatProcessor

class ClangFormatExternalFormatProcessor : ExternalFormatProcessor {
  override fun activeForFile(file: PsiFile): Boolean {
    return service<ClangFormatService>().mayBeFormatted(file)
  }

  override fun format(
    source: PsiFile,
    range: TextRange,
    canChangeWhiteSpacesOnly: Boolean,
    keepLineBreaks: Boolean,
    enableBulkUpdate: Boolean,
    cursorOffset: Int
  ): TextRange? {
    val virtualFile = source.originalFile.virtualFile
    if (virtualFile != null) {
      ProgressManager.checkCanceled()
      service<ClangFormatService>().reformatFileSync(source.project, virtualFile)
      return range
    }
    return null
  }

  override fun indent(source: PsiFile, lineStartOffset: Int): String? {
    return null
  }

  override fun getId(): String {
    return "aarcangeli.clang-format"
  }
}
