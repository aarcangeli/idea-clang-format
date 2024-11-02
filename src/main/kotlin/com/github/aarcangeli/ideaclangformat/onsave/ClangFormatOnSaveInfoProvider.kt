package com.github.aarcangeli.ideaclangformat.onsave

import com.github.aarcangeli.ideaclangformat.utils.ClangFormatCommons
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider

class ClangFormatOnSaveInfoProvider : ActionOnSaveInfoProvider() {
  override fun getActionOnSaveInfos(context: ActionOnSaveContext): Collection<ActionOnSaveInfo> {
    if (ClangFormatCommons.isUsingCustomFormatOnSave()) {
      return mutableListOf(ClangFormatOnSaveActionInfo(context))
    }
    return emptyList()
  }
}
