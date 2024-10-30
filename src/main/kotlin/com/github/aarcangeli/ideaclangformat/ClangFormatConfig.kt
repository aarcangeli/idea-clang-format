package com.github.aarcangeli.ideaclangformat

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import com.jetbrains.rd.generator.nova.PredefinedType
import org.jetbrains.debugger.values.PrimitiveValue.Companion.bool

/**
 * Application-wide configuration for the ClangFormat plugin.
 */
@State(name = "ClangFormatConfig", storages = [(Storage("clang-format.xml"))])
class ClangFormatConfig : SimplePersistentStateComponent<ClangFormatConfig.State>(State()) {

  class State : BaseState() {
    var enabled by property(true)

    var formatOnSave by property(false)

    /// The path to the clang-format executable.
    /// If null, the plugin will try to find it in the PATH.
    var path by string()
  }
}
