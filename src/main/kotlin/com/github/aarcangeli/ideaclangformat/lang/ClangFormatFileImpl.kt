package com.github.aarcangeli.ideaclangformat.lang

import com.intellij.psi.FileViewProvider
import org.jetbrains.yaml.psi.impl.YAMLFileImpl

class ClangFormatFileImpl(viewProvider: FileViewProvider) : YAMLFileImpl(viewProvider), ClangFormatFile
