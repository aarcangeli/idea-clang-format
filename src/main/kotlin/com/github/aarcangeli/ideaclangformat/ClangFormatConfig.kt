package com.github.aarcangeli.ideaclangformat

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Property

@State(name = "ClangFormatConfig", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))])
class ClangFormatConfig(private val project: Project) : PersistentStateComponent<ClangFormatConfig.State> {

  private var state = State()

  fun isFormatOnSaveEnabled(): Boolean {
    return state.formatOnSaveEnabled
  }

  fun setFormatOnSaveEnabled(enabled: Boolean) {
    state.formatOnSaveEnabled = enabled
  }

  override fun getState(): State {
    return state
  }

  override fun loadState(state: State) {
    this.state = state
  }

  data class State(@Property var formatOnSaveEnabled: Boolean = false)
}
