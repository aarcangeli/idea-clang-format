package com.github.aarcangeli.ideaclangformat.configurable

import com.github.aarcangeli.ideaclangformat.services.ClangFormatInstallationManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.ui.JBUI
import javax.swing.JList


class ClangFormatVersionComboBox : ComboBox<Any>() {
  init {
    // enable speed search
    isSwingPopup = false
    renderer = MyCellRenderer()
    setMinimumAndPreferredWidth(JBUI.scale(300))
    updateComboBox()
  }

  private fun updateComboBox() {
    removeAllItems()
    service<ClangFormatInstallationManager>().getReleaseList().forEach {
      addItem(it.version)
    }
    addItem(DownloadRelease())
  }

  private class MyCellRenderer : ColoredListCellRenderer<Any>() {
    override fun customizeCellRenderer(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value is String) {
        append("Version $value")
      }
      else if (value is DownloadRelease) {
        append("Download...")
        icon = AllIcons.Actions.Download
      }
    }
  }

  override fun setSelectedItem(anObject: Any?) {
    //if (anObject is UnrealEngineInstallation) {
    //  super.setSelectedItem(anObject)
    //}
    //else if (anObject is AddSdkAction) {
    //  addSdk()
    //}
  }

  class DownloadRelease
}
