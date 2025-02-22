package com.github.aarcangeli.ideaclangformat.utils

import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
import com.github.aarcangeli.ideaclangformat.exceptions.ClangMissingLanguageException
import com.github.aarcangeli.ideaclangformat.exceptions.ClangValidationError
import com.intellij.build.FileNavigatable
import com.intellij.build.FilePosition
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.PathUtil
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

// TODO: We should respect the user's settings for the file extensions
val knownCppExtensions = setOf(
  "c", "cp", "cpp", "cppm", "c++", "cxx", "cc", "cu",
  "ino", "ixx",
  "h", "hh", "hpp", "hxx", "inc", "inl", "ipp", "mpp", "pch", "tch", "tpp", "cuh",
)

object ClangFormatCommons {
  private val CLANG_ERROR_PATTERN = Pattern.compile(
    "(?<FileName>(?:[a-zA-Z]:|/)[^<>|?*:\\t]+):(?<LineNumber>\\d+):(?<Column>\\d+)\\s*:\\s*(?<Type>\\w+):\\s*(?<Message>.*)"
  )

  fun isCppFile(file: PsiFile): Boolean {
    val extension = PathUtil.getFileExtension(file.name.lowercase()) ?: return false
    return extension in knownCppExtensions
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

  fun isClangFormatFile(filename: String): Boolean {
    return filename.equals(".clang-format", ignoreCase = true) ||
      filename.equals("_clang-format", ignoreCase = true)
  }

  @Throws(ClangValidationError::class)
  fun getException(project: Project, commandLine: GeneralCommandLine, output: ProcessOutput): ClangFormatError {
    var stderr = output.stderr
    if (stderr.startsWith("Configuration file(s) do(es) not support")) {
      return ClangMissingLanguageException(stderr.trim { it <= ' ' })
    }
    val matcher = CLANG_ERROR_PATTERN.matcher(stderr)
    if (matcher.find()) {
      try {
        val fileName = File(matcher.group("FileName").replace('\\', '/'))
        val lineNumber = matcher.group("LineNumber").toInt()
        val column = matcher.group("Column").toInt()
        val type = matcher.group("Type")
        val message = matcher.group("Message")
        if (type == "error") {
          val description = """
                        ${fileName.name}:$lineNumber:$column: $message
                        ${stderr.substring(matcher.group(0).length).trim { it <= ' ' }}
                        """.trimIndent()
          return ClangValidationError(
            description,
            FileNavigatable(project, FilePosition(fileName, lineNumber - 1, column - 1))
          )
        }
      }
      catch (ignored: NumberFormatException) {
        // in case of overflow
      }
    }
    if (stderr.trim().isEmpty()) {
      // nothing on stderr, we use stdout instead
      stderr = output.stdout
    }
    return ClangFormatError("Exit code ${output.exitCode} from ${commandLine.commandLineString}\n${stderr}")
  }

  fun getClangFormatPathFromResources(): InputStream? {
    val resourcePath = when {
      SystemInfo.isWindows -> "/clang-format-win/clang-format.exe"
      SystemInfo.isLinux && SystemInfo.isAarch64 -> "/clang-format-linux-aarch64/clang-format"
      SystemInfo.isLinux -> "/clang-format-linux-armv7a/clang-format"
      SystemInfo.isMac && SystemInfo.isAarch64 -> "/clang-format-macos-arm64/clang-format"
      SystemInfo.isMac -> "/clang-format-macos-x64/clang-format"
      else -> return null
    }
    return ClangFormatCommons::class.java.getResourceAsStream(resourcePath)
  }

  fun readBuiltInVersion(): String {
    val inputStream = ClangFormatCommons::class.java.getResourceAsStream("/clang-format-tag.txt")
    val version = inputStream?.bufferedReader()?.use { it.readText() } ?: return "unknown"
    return version.split("-")[1].trim()
  }

  fun createCommandLine(clangFormatPath: String): GeneralCommandLine {
    val commandLine = GeneralCommandLine()
    if (SystemInfo.isWindows && isWinShellScript(clangFormatPath)) {
      commandLine.exePath = "cmd.exe"
      commandLine.addParameter("/c")
      commandLine.addParameter(clangFormatPath)
    }
    else {
      commandLine.exePath = clangFormatPath
    }
    commandLine.addParameter("--fno-color-diagnostics")
    return commandLine
  }

  private fun isWinShellScript(command: @NonNls String?): Boolean {
    return endsWithIgnoreCase(command, ".cmd") || endsWithIgnoreCase(command, ".bat")
  }

  private fun endsWithIgnoreCase(str: String?, suffix: String): Boolean {
    return str!!.regionMatches(str.length - suffix.length, suffix, 0, suffix.length, ignoreCase = true)
  }

  fun getUnsavedClangFormats(): Array<Document> {
    val documents = ArrayList<Document>()
    for (document in FileDocumentManager.getInstance().unsavedDocuments) {
      val file = FileDocumentManager.getInstance().getFile(document) ?: continue
      if (isClangFormatFile(file.name) && document.isWritable) {
        documents.add(document)
      }
    }
    return documents.toTypedArray()
  }

  fun getFileName(virtualFile: VirtualFile): String {
    var it = virtualFile
    if (it is LightVirtualFile) {
      it = it.originalFile
    }
    if (it.isInLocalFileSystem) {
      return it.path
    }
    return virtualFile.name
  }

  /**
   * Rider doesn't work with "Reformat on save" option, so we need to create a custom provider.
   */
  fun isUsingCustomFormatOnSave(): Boolean {
    return isRider()
  }

  private fun isRider(): Boolean {
    val prefix = System.getProperty("idea.platform.prefix", null)
    val isRider = prefix == "Rider"
    return isRider
  }
}
