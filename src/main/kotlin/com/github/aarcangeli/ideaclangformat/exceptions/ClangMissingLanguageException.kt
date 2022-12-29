package com.github.aarcangeli.ideaclangformat.exceptions

import org.jetbrains.annotations.Nls

/**
 * Thrown when no ".clang-format" files support the file language.
 * eg: "Configuration file(s) do(es) not support CSharp: /path/.clang-format"
 */
class ClangMissingLanguageException(message: @Nls String) : ClangFormatError(message)
