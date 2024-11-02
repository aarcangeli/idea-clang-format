package com.github.aarcangeli.ideaclangformat

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-wide configuration for the ClangFormat plugin.
 */
@State(name = "ClangFormatConfig", storages = [(Storage("clang-format.xml"))])
class ClangFormatConfig : SimplePersistentStateComponent<ClangFormatConfig.State>(State()) {

  class State : BaseState() {
    var enabled by property(true)

    /// The path to the clang-format executable.
    /// If null, the plugin will try to find it in the PATH.
    var customPath by string()
  }
}
