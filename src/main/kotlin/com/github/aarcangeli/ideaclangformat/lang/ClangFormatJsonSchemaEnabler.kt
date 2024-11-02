package com.github.aarcangeli.ideaclangformat.lang

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler

class ClangFormatJsonSchemaEnabler : JsonSchemaEnabler {
  override fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean {
    return file.fileType == ClangFormatFileType.INSTANCE
  }

  override fun shouldShowSwitcherWidget(file: VirtualFile?): Boolean {
    return true
  }
}
