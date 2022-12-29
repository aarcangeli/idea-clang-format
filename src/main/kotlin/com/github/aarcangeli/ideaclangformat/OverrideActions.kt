package com.github.aarcangeli.ideaclangformat

import com.github.aarcangeli.ideaclangformat.actions.ReformatCodeWithClangAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

private val LOG = Logger.getInstance(OverrideActions::class.java)

class OverrideActions : StartupActivity {
  override fun runActivity(project: Project) {
    val actionId = "ReformatCode"
    val oldAction = ActionManager.getInstance().getAction(actionId)
    if (oldAction == null) {
      LOG.warn("Action not found $actionId")
      return
    }
    val newAction: AnAction = ReformatCodeWithClangAction(oldAction)
    LOG.info("Overriding built-in action $actionId (${oldAction.javaClass.name}) with ${newAction.javaClass.name}")
    ActionManager.getInstance().replaceAction(actionId, newAction)
  }
}
