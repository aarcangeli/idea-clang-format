package com.github.aarcangeli.ideaclangformat.onsave

import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider

class ClangFormatOnSaveInfoProvider : ActionOnSaveInfoProvider() {
  override fun getActionOnSaveInfos(context: ActionOnSaveContext): MutableCollection<out ActionOnSaveInfo> {
    return mutableListOf(ClangFormatOnSaveActionInfo(context))
  }
}
