package com.github.aarcangeli.ideaclangformat.exceptions

import com.intellij.build.FileNavigatable
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.Nls

class ClangExitCodeError(@JvmField val description: @Nls String, private val fileNavigatable: FileNavigatable) :
  ClangFormatError(description) {

  fun getFileNavigatable(): Navigatable {
    return fileNavigatable
  }
}
