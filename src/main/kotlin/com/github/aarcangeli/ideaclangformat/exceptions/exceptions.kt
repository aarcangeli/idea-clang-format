package com.github.aarcangeli.ideaclangformat.exceptions

import com.github.aarcangeli.ideaclangformat.MyBundle
import com.intellij.build.FileNavigatable
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.Nls

open class ClangFormatError : RuntimeException {
  constructor(message: @Nls String) : super(message)
  constructor(message: @Nls String, cause: Throwable?) : super(message, cause)
}

class ClangFormatNotFound : ClangFormatError(MyBundle.message("error.clang-format.error.not-found"))

/**
 * Error when clang-format reports an error on the style file.
 */
class ClangValidationError(@JvmField val description: @Nls String, private val fileNavigatable: FileNavigatable) :
  ClangFormatError(description) {

  fun getFileNavigatable(): Navigatable {
    return fileNavigatable
  }
}

/**
 * Thrown on any exit code different from 0.
 */
class ClangExitCode(val exitCode: Int) : ClangFormatError("Clang-format exited with code $exitCode")

/**
 * Thrown when no ".clang-format" files support the file language.
 * eg: "Configuration file(s) do(es) not support CSharp: /path/.clang-format"
 */
class ClangMissingLanguageException(message: @Nls String) : ClangFormatError(message)
