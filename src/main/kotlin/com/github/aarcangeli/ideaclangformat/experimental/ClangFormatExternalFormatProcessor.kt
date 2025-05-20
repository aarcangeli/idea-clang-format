package com.github.aarcangeli.ideaclangformat.experimental

import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.ExternalFormatProcessor

private val LOG = Logger.getInstance(ClangFormatExternalFormatProcessor::class.java)

class ClangFormatExternalFormatProcessor : ExternalFormatProcessor {
  override fun activeForFile(file: PsiFile): Boolean {
    return service<ClangFormatService>().mayBeFormatted(file, true)
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
      LOG.debug("Reformatting file: ${virtualFile.path}")
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
