package com.github.aarcangeli.ideaclangformat.services

import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Provides utilities to find and parse .clang-format files
 */
interface ClangFormatStyleService {
  /**
   * Retrieve the .clang-format file for the specified file.
   */
  @RequiresReadLock
  fun getStyleFile(virtualFile: VirtualFile): VirtualFile?

  /**
   * Returns true if there is a .clang-format file is found for the specified file.
   */
  @RequiresReadLock
  fun isThereStyleForFile(virtualFile: VirtualFile): Boolean

  @Throws(ClangFormatError::class)
  fun getRawFormatStyle(psiFile: PsiFile): Map<String, Any>

  /**
   * Returns a tracker that should be invalidated when the .clang-format file changes.
   * This method also covers the case when the .clang-format file is added or removed.
   */
  @RequiresReadLock
  fun makeDependencyTracker(file: PsiFile): ModificationTracker
}
