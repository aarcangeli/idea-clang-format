package com.github.aarcangeli.ideaclangformat.services

import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Provides functionalities to format a document using clang-format
 */
interface ClangFormatService {
  /**
   * Returns true if a ".clang-format" is available for the specified file.
   * The file must also be with a matching language.
   * The result of this value may be cached using the tracker returned by "makeDependencyTracker"
   *
   * @throws ClangFormatError if an error occurs while checking the file
   * @return
   */
  fun mayBeFormatted(file: PsiFile, inCaseOfStyleError: Boolean): Boolean

  /**
   * A list of possible paths to the clang-format binary.
   */
  fun detectFromPath(): String?

  @get:Throws(ClangFormatError::class)
  val clangFormatPath: String?

  /**
   * Reformat the specified file
   */
  @RequiresEdt
  fun reformatFileSync(project: Project, virtualFile: VirtualFile)

  /**
   * Reformat the specified file asynchronously, the operation is completed later.
   */
  @RequiresEdt
  fun reformatInBackground(project: Project, virtualFile: VirtualFile)

  /**
   * Remove the last notification displayed by the service.
   */
  fun clearErrorNotification()

  /**
   * @throws ClangFormatError
   */
  fun validatePath(path: String): String

  companion object {
    const val GROUP_ID = "aarcangeli.notification.ClangFormat"
  }
}
