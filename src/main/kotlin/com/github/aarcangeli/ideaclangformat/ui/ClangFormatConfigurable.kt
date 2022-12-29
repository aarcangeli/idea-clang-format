package com.github.aarcangeli.ideaclangformat.ui

import com.github.aarcangeli.ideaclangformat.MyBundle
import com.github.aarcangeli.ideaclangformat.ClangFormatSettings
import com.intellij.application.options.GeneralCodeStyleOptionsProvider
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.ui.dsl.builder.panel
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
