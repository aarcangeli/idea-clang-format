package com.github.aarcangeli.ideaclangformat.services

import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Provides functionalities to format a document using clang-format
 */
interface ClangFormatService {
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

  @Throws(ClangFormatError::class)
  fun getRawFormatStyle(psiFile: PsiFile): Map<Any?, Any?>

  @get:Throws(ClangFormatError::class)
  val clangFormatPath: String

  /**
   * Returns a tracker that changes when configuration of a specific file changes
   */
  @RequiresReadLock
  fun makeDependencyTracker(file: PsiFile): ModificationTracker?
  fun getStyleFile(virtualFile: VirtualFile): VirtualFile?

  /**
   * Returns true if a ".clang-format" is available for the specified file.
   * The file must also be with a matching language.
   * The result of this value may be cached using the tracker returned by "makeDependencyTracker"
   *
   * @return
   */
  fun mayBeFormatted(file: PsiFile): Boolean

  companion object {
    val instance: ClangFormatService
      get() = ApplicationManager.getApplication().getService(
        ClangFormatService::class.java
      )
    const val GROUP_ID = "aarcangeli.notification.ClangFormat"
  }
}
