package com.github.aarcangeli.ideaclangformat.lang

import com.github.aarcangeli.ideaclangformat.utils.ClangFormatCommons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.function.Function
import javax.swing.JComponent

/**
 * Show an error if .clang-format is not saved in UTF-8
 */
class ClangEditorNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(
    project: Project,
    file: VirtualFile
  ): Function<in FileEditor, out JComponent?>? {
    if (ClangFormatCommons.isClangFormatFile(file.name)) {
      return Function { fileEditor -> createPanel(fileEditor) }
    }
    return null
  }

  @RequiresEdt
  private fun createPanel(
    fileEditor: FileEditor,
  ): EditorNotificationPanel? {
    if (fileEditor.file.charset.name() != "UTF-8") {
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Error)
      panel.text = "ClangFormat file is not UTF-8 encoded"
      return panel
    }
    return null
  }
}

/**
 * Ensure that the editor notification is updated when the file encoding changes.
 */
class ClangFileListener(val project: Project) : BulkFileListener {
  override fun after(events: List<VFileEvent>) {
    for (event in events) {
      if (event is VFilePropertyChangeEvent) {
        if (event.propertyName == VirtualFile.PROP_ENCODING) {
          if (ClangFormatCommons.isClangFormatFile(event.file.name)) {
            EditorNotifications.getInstance(project).updateNotifications(event.file)
          }
        }
      }
    }
  }
}
