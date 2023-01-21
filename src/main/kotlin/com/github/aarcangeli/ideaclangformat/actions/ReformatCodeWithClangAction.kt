package com.github.aarcangeli.ideaclangformat.actions

import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.github.aarcangeli.ideaclangformat.utils.ClangFormatCommons
import com.intellij.codeInsight.actions.ReformatCodeAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

/**
 * This action is faster than the original [ReformatCodeAction] when used with a single file.
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
    val editor = CommonDataKeys.EDITOR.getData(dataContext)
    if (editor != null) {
      val virtualFile = ClangFormatCommons.getVirtualFileFor(project, editor.document)
      if (virtualFile != null) {
        service<ClangFormatService>().reformatInBackground(project, virtualFile)
        return true
      }
    }
    return false
  }

  private fun isManaged(event: AnActionEvent): Boolean {
    val dataContext = event.dataContext
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
    val editor = CommonDataKeys.EDITOR.getData(dataContext)
    return if (editor != null) {
      ClangFormatCommons.getVirtualFileFor(project, editor.document) != null
    }
    else false
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
