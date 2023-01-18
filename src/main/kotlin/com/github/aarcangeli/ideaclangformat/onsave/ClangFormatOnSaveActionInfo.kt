// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.github.aarcangeli.ideaclangformat.onsave

import com.github.aarcangeli.ideaclangformat.ClangFormatConfig
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.openapi.components.service

class ClangFormatOnSaveActionInfo(context: ActionOnSaveContext) : ActionOnSaveInfo(context) {
  private var isFormatOnSaveEnabled: Boolean = context.project.service<ClangFormatConfig>().isFormatOnSaveEnabled()

  override fun apply() {
    context.project.service<ClangFormatConfig>().setFormatOnSaveEnabled(isFormatOnSaveEnabled)
  }

  override fun isModified(): Boolean {
    return isFormatOnSaveEnabled != context.project.service<ClangFormatConfig>().isFormatOnSaveEnabled()
  }

  override fun getActionOnSaveName(): String {
    return "Clang-Format"
  }

  override fun isActionOnSaveEnabled(): Boolean {
    return isFormatOnSaveEnabled
  }

  override fun setActionOnSaveEnabled(enabled: Boolean) {
    isFormatOnSaveEnabled = enabled
  }
}
