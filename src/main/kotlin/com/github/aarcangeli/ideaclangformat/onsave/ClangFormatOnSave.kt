package com.github.aarcangeli.ideaclangformat.onsave

import com.github.aarcangeli.ideaclangformat.ClangFormatConfig
import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener.ActionOnSave
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

class ClangFormatOnSave : ActionOnSave() {
  override fun isEnabledForProject(project: Project): Boolean {
    return service<ClangFormatConfig>().state.formatOnSave
  }

  override fun processDocuments(project: Project, documents: Array<Document?>) {
    val fileDocumentManager = service<FileDocumentManager>()

    for (document in documents) {
      val virtualFile = fileDocumentManager.getFile(document ?: continue) ?: continue
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: continue
      if (service<ClangFormatService>().mayBeFormatted(psiFile)) {
        service<ClangFormatService>().reformatFileSync(project, virtualFile)
      }
    }
  }
}
