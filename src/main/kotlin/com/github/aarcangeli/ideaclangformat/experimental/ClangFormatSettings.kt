package com.github.aarcangeli.ideaclangformat.experimental

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class ClangFormatSettings(container: CodeStyleSettings) : CustomCodeStyleSettings("clang-format", container) {
    @JvmField
    var ENABLED = true
}
