package com.github.aarcangeli.ideaclangformat.configurable

import com.github.aarcangeli.ideaclangformat.ClangFormatConfig
import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import java.awt.event.ActionEvent
import javax.swing.JLabel

class AppConfigurable : DslConfigurableBase(), SearchableConfigurable, NoScroll {
  private lateinit var combobox: Cell<JBCheckBox>

  private val settings = service<ClangFormatConfig>().state
  private lateinit var version: Cell<JLabel>
  private lateinit var pathField: Cell<TextFieldWithBrowseButton>

  override fun getId(): String = "aarcangeli.ideaclangformat.appconfig"
  override fun getDisplayName(): String = "Clang-Format Tools"

  override fun createPanel(): DialogPanel {
    return panel {
      group("Options") {
        row {
          combobox = checkBox("Enable Clang-Format support")
            .comment("When disabled, Clang-Format will not be used")
            .bindSelected(settings::enabled)
            .onApply {
              if (!settings.enabled) {
                service<ClangFormatService>().clearErrorNotification()
              }
              for (project in service<ProjectManager>().openProjects) {
                CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
              }
            }
        }
      }

      group("Location") {
        row("Auto-detected:") {
          val detectFromPath = service<ClangFormatService>().detectFromPath()
          textField()
            .align(AlignX.FILL)
            .component.apply {
              text = detectFromPath ?: "Not found"
              isEditable = false
            }
        }
        row("Custom Path:") {
          pathField = textFieldWithBrowseButton("Clang-Format Path")
            .align(AlignX.FILL)
            .bindText({ settings.customPath ?: "" }, { settings.customPath = it })
        }
        row {
          button("Test", ::testExe)
        }
        row {
          version = label("")
        }
      }
    }
  }

  private fun testExe(action: ActionEvent) {
    var path = pathField.component.text
    if (path.isBlank()) {
      path = service<ClangFormatService>().detectFromPath() ?: ""
    }
    if (path.isBlank()) {
      version.component.text = ""
      return
    }
    try {
      version.component.text = "Using " + service<ClangFormatService>().validatePath(path)
    }
    catch (e: ClangFormatError) {
      version.component.text = "Invalid path '$path': ${e.message}"
    }
  }
}
