package com.github.aarcangeli.ideaclangformat.onsave

import com.github.aarcangeli.ideaclangformat.ClangFormatConfig
import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.github.aarcangeli.ideaclangformat.services.ClangFormatStyleServiceImpl
import com.github.aarcangeli.ideaclangformat.utils.ClangFormatCommons
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener.ActionOnSave
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

private val LOG = Logger.getInstance(ClangFormatOnSave::class.java)

class ClangFormatOnSave : ActionOnSave() {
  override fun isEnabledForProject(project: Project): Boolean {
    return service<ClangFormatConfig>().state.formatOnSave && ClangFormatCommons.isUsingCustomFormatOnSave()
  }

  override fun processDocuments(project: Project, documents: Array<Document>) {
    val fileDocumentManager = service<FileDocumentManager>()

    for (document in documents) {
      val virtualFile = fileDocumentManager.getFile(document) ?: continue
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: continue
      if (service<ClangFormatService>().mayBeFormatted(psiFile, false)) {
        LOG.info("Reformatting file on save: ${virtualFile.path}")
        service<ClangFormatService>().reformatFileSync(project, virtualFile)
      } else {
        LOG.info("File cannot be formatted by clang-format: ${virtualFile.path}")
      }
    }
  }
}
