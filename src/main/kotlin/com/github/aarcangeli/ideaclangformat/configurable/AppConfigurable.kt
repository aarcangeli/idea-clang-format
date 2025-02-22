package com.github.aarcangeli.ideaclangformat.configurable

import com.github.aarcangeli.ideaclangformat.ClangFormatConfig
import com.github.aarcangeli.ideaclangformat.ClangFormatToUse
import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.github.aarcangeli.ideaclangformat.utils.ClangFormatCommons
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBFont
import java.awt.event.ActionEvent
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JRadioButton
import javax.swing.JTextArea
import kotlin.io.path.pathString

class AppConfigurable : DslConfigurableBase(), SearchableConfigurable, NoScroll {
  private lateinit var combobox: Cell<JBCheckBox>

  private val settings = service<ClangFormatConfig>().state
  private lateinit var version: JTextArea
  private lateinit var pathField: Cell<TextFieldWithBrowseButton>

  override fun getId(): String = "aarcangeli.ideaclangformat.appconfig"
  override fun getDisplayName(): String = "Clang-Format Tools"

  private lateinit var builtinRadio: JRadioButton
  private lateinit var detectRadio: JRadioButton
  private lateinit var customRadio: JRadioButton

  override fun createPanel(): DialogPanel {
    return panel {
      group("Options") {
        row {
          combobox = checkBox("Enable reformatting with Clang-Format")
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
        if (ClangFormatCommons.isUsingCustomFormatOnSave()) {
          row {
            combobox = checkBox("Format on save")
              .bindSelected(settings::formatOnSave)
              .enabledIf(combobox.selected)
          }
        }
      }

      group("Location") {
        buttonsGroup {
          row {
            val builtin = service<ClangFormatService>().getBuiltinPath()
            builtinRadio = radioButton("Built-in (${builtin?.version ?: "not available"})")
              .bindSelected({ settings.location == ClangFormatToUse.BUILTIN }, { settings.location = ClangFormatToUse.BUILTIN })
              .gap(RightGap.SMALL)
              .onChanged { resetVersion() }
              .enabled(builtin != null)
              .component
          }
          row {
            val detectFromPath = service<ClangFormatService>().detectFromPath()
            detectRadio = radioButton("Auto-detected (${detectFromPath ?: "Not found"})")
              .bindSelected(
                { settings.location == ClangFormatToUse.AUTO_DETECT },
                { settings.location = ClangFormatToUse.AUTO_DETECT })
              .onChanged { resetVersion() }
              .enabled(detectFromPath != null)
              .component
          }
          row {
            customRadio = radioButton("Custom:")
              .bindSelected({ settings.location == ClangFormatToUse.CUSTOM }, { settings.location = ClangFormatToUse.CUSTOM })
              .gap(RightGap.SMALL)
              .onChanged { resetVersion() }
              .component

            pathField = textFieldWithBrowseButton("Clang-Format Path")
              .align(AlignX.FILL)
              .validationOnInput {
                val path = Paths.get(it.text.trim())
                if (path.pathString.isBlank()) {
                  return@validationOnInput ValidationInfo("Path cannot be empty")
                }
                if (!Files.exists(path)) {
                  return@validationOnInput ValidationInfo("Path does not exist")
                }
                if (!Files.isRegularFile(path)) {
                  return@validationOnInput ValidationInfo("Path is not a file")
                }
                if (!Files.isExecutable(path)) {
                  return@validationOnInput ValidationInfo("Path is not executable")
                }
                return@validationOnInput null
              }
              .bindText({ settings.customPath ?: "" }, { settings.customPath = it })
              .onChanged { resetVersion() }
              .enabledIf(customRadio.selected)
          }
        }
        row {
          button("Test", ::testExe)
        }
        row {
          version = JTextArea().apply {
            isEditable = false
            border = null
            lineWrap = true
            wrapStyleWord = true
            background = null
            toolTipText = "Click 'Test' to check the path"
            isVisible = false
            font = JBFont.label()
          }
          cell(version)
            .align(AlignX.FILL)
        }
      }
    }
  }

  private fun resetVersion() {
    version.text = ""
    version.isVisible = false
  }

  private fun testExe(action: ActionEvent) {
    version.isVisible = true
    val path = getFinalPath() ?: ""
    if (path.isBlank()) {
      version.text = "Invalid path"
      return
    }
    try {
      val versionStr = service<ClangFormatService>().validatePath(path)
      version.text = "$versionStr\n\n$path"
    }
    catch (e: ClangFormatError) {
      version.text = "${e.message}\n\n$path"
    }
  }

  private fun getFinalPath(): String? {
    if (builtinRadio.isSelected) {
      return service<ClangFormatService>().getBuiltinPath()?.path
    }
    if (detectRadio.isSelected) {
      return service<ClangFormatService>().detectFromPath()
    }
    return pathField.component.text.trim()
  }
}
