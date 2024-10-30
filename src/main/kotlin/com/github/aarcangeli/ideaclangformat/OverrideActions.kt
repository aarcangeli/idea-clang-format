package com.github.aarcangeli.ideaclangformat

import com.github.aarcangeli.ideaclangformat.actions.ReformatCodeWithClangAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private val LOG = Logger.getInstance(OverrideActions::class.java)

class OverrideActions : ProjectActivity {
  override suspend fun execute(project: Project) {
    val actionId = "ReformatCode"
    val oldAction = ActionManager.getInstance().getAction(actionId)
    if (oldAction == null) {
      LOG.warn("Action not found $actionId")
      return
    }
    val baseAction: AnAction = ReformatCodeWithClangAction(oldAction)
    LOG.info("Overriding built-in action $actionId (${oldAction.javaClass.name}) with ${baseAction.javaClass.name}")
    ActionManager.getInstance().replaceAction(actionId, baseAction)
  }
}
