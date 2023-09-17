package com.github.aarcangeli.ideaclangformat.configurable

import com.github.aarcangeli.ideaclangformat.MyBundle
import com.github.aarcangeli.ideaclangformat.experimental.ClangFormatSettings
import com.intellij.application.options.GeneralCodeStyleOptionsProvider
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import javax.swing.JCheckBox
import javax.swing.JComponent

class ClangFormatConfigurable : CodeStyleSettingsProvider(), GeneralCodeStyleOptionsProvider {
  private lateinit var myEnabled: JCheckBox

  override fun createComponent(): JComponent {
    return panel {
      group(MyBundle.message("clang-format.title")) {
        row {
          myEnabled = checkBox(MyBundle.message("clang-format.enable"))
            .comment(MyBundle.message("clang-format.comment"))
            .component
        }

        row(MyBundle.message("clang-format.config.version")) {
          cell(ClangFormatVersionComboBox())
        }

        row(MyBundle.message("clang-format.config.path")) {
          val fileChooserDescriptor = FileChooserDescriptor(true, false, false, false, false, false)
          textFieldWithBrowseButton(MyBundle.message("clang-format.config.path"), fileChooserDescriptor = fileChooserDescriptor)
            .horizontalAlign(HorizontalAlign.FILL)
          //button(MyBundle.message("clang-format.config.path.test")) {
          //}
        }
      }
    }
  }

  override fun isModified(settings: CodeStyleSettings): Boolean {
    return myEnabled.isSelected != settings.getCustomSettings(ClangFormatSettings::class.java).ENABLED
  }

  override fun apply(settings: CodeStyleSettings) {
    settings.getCustomSettings(ClangFormatSettings::class.java).ENABLED = myEnabled.isSelected
  }

  override fun reset(settings: CodeStyleSettings) {
    myEnabled.isSelected = settings.getCustomSettings(ClangFormatSettings::class.java).ENABLED
  }

  override fun isModified(): Boolean = false

  override fun apply() {
  }

  override fun hasSettingsPage(): Boolean = false

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
    return ClangFormatSettings(settings)
  }
}
