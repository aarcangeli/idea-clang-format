package com.github.aarcangeli.ideaclangformat.exceptions

import org.jetbrains.annotations.Nls

open class ClangFormatError : RuntimeException {
  constructor(message: @Nls String) : super(message)
  constructor(message: @Nls String, cause: Throwable?) : super(message, cause)
}
