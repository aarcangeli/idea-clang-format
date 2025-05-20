package com.github.aarcangeli.ideaclangformat.actions

import com.github.aarcangeli.ideaclangformat.experimental.ClangFormatExternalFormatProcessor
import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.github.aarcangeli.ideaclangformat.utils.ClangFormatCommons
import com.intellij.codeInsight.actions.ReformatCodeAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager

private val LOG = Logger.getInstance(ReformatCodeWithClangAction::class.java)

/**
 * Rider doesn't support ExternalFormatProcessor, so this is the only way to reformat code in Rider.
 */
class ReformatCodeWithClangAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    // Only available in Rider
    if (!ClangFormatCommons.isRider()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabled = isManaged(e)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val dataContext = event.dataContext
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
    val virtualFile = getVirtualFileFor(project, editor.document) ?: return
    LOG.info("Reformatting file: ${virtualFile.path}")
    service<ClangFormatService>().reformatFileSync(project, virtualFile)
  }

  private fun isManaged(event: AnActionEvent): Boolean {
    val dataContext = event.dataContext
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
    return getVirtualFileFor(project, editor.document) != null
  }

  private fun getVirtualFileFor(project: Project, document: Document): VirtualFile? {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    if (psiFile != null && service<ClangFormatService>().mayBeFormatted(psiFile, true)) {
      return psiFile.originalFile.virtualFile
    }
    return null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
