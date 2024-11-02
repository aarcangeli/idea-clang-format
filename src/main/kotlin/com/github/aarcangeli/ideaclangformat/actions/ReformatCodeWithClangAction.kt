package com.github.aarcangeli.ideaclangformat.actions

import com.github.aarcangeli.ideaclangformat.experimental.ClangFormatExternalFormatProcessor
import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.intellij.codeInsight.actions.ReformatCodeAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager

/**
 * This action is a little faster than the original [ReformatCodeAction] when used with a single file.
 * This uses an asynchronous reformatting process, so it doesn't block the UI.
 * The alternative way is [ClangFormatExternalFormatProcessor], which is synchronous and blocks the UI.
 */
class ReformatCodeWithClangAction(private val baseAction: AnAction) : AnAction(), DumbAware, OverridingAction {
  init {
    templatePresentation.copyFrom(baseAction.templatePresentation)
  }

  override fun update(e: AnActionEvent) {
    if (!isManaged(e)) {
      baseAction.update(e)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!handleAction(e)) {
      baseAction.actionPerformed(e)
    }
  }

  private fun handleAction(event: AnActionEvent): Boolean {
    val dataContext = event.dataContext
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
    val virtualFile = getVirtualFileFor(project, editor.document) ?: return false
    service<ClangFormatService>().reformatInBackground(project, virtualFile)
    return true
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
