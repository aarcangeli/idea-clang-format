package com.github.aarcangeli.ideaclangformat.utils

import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

object ClangFormatCommons {
  fun isCppFile(file: PsiFile): Boolean {
    // TODO: add more extensions
    val filename = file.name.lowercase()
    return filename.endsWith(".cpp") || filename.endsWith(".h") || filename.endsWith(".h")
  }

  fun isUnconditionallyNotSupported(file: PsiFile): Boolean {
    val virtualFile = file.originalFile.virtualFile
    return isUnconditionallyNotSupported(virtualFile)
  }

  fun isUnconditionallyNotSupported(virtualFile: VirtualFile?): Boolean {
    // invalid virtual file
    return virtualFile == null ||
      !virtualFile.isValid ||
      !virtualFile.isInLocalFileSystem ||
      isClangFormatFile(virtualFile.name)
  }

  fun getVirtualFileFor(project: Project, document: Document): VirtualFile? {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    if (psiFile != null && service<ClangFormatService>().mayBeFormatted(psiFile)) {
      return psiFile.originalFile.virtualFile
    }
    return null
  }

  fun isClangFormatFile(filename: String): Boolean {
    return filename.equals(".clang-format", ignoreCase = true) ||
      filename.equals("_clang-format", ignoreCase = true)
  }
}
