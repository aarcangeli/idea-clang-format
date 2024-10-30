package com.github.aarcangeli.ideaclangformat.configurable

import com.github.aarcangeli.ideaclangformat.ClangFormatConfig
import com.github.aarcangeli.ideaclangformat.MyBundle
import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class AppConfigurable : DslConfigurableBase(), SearchableConfigurable, NoScroll {
  private lateinit var combobox: Cell<JBCheckBox>

  private val settings = service<ClangFormatConfig>().state

  override fun getId(): String = "aarcangeli.ideaclangformat.appconfig"
  override fun getDisplayName(): String = "Clang-Format Tools"

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        combobox = checkBox(MyBundle.message("clang-format.enable"))
          .comment("Enable ClangFormat for the application")
          .bindSelected(settings::enabled)
          .onApply {
            if (!settings.enabled) {
              // do something
              service<ClangFormatService>().clearErrorNotification()
            }
          }
      }
      row {
        combobox = checkBox("Format on save")
          .bindSelected(settings::formatOnSave)
      }
    }
  }
}
