package com.github.aarcangeli.ideaclangformat.services

import com.github.aarcangeli.ideaclangformat.ClangFormatConfig
import com.github.aarcangeli.ideaclangformat.ClangFormatToUse
import com.github.aarcangeli.ideaclangformat.MyBundle.message
import com.github.aarcangeli.ideaclangformat.exceptions.ClangExitCode
import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
import com.github.aarcangeli.ideaclangformat.exceptions.ClangValidationError
import com.github.aarcangeli.ideaclangformat.utils.ClangFormatCommons
import com.github.aarcangeli.ideaclangformat.utils.OffsetConverter
import com.github.aarcangeli.ideaclangformat.utils.ProcessUtils
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.core.CoreBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.notification.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val LOG = Logger.getInstance(ClangFormatServiceImpl::class.java)

/**
 * See class ApplyChangesState
 */
private const val BULK_REPLACE_OPTIMIZATION_CRITERIA = 1000

class ClangFormatServiceImpl : ClangFormatService, Disposable {
  private val errorNotification = AtomicReference<Notification?>()
  private val afterWriteActionFinished = ContainerUtil.createLockFreeCopyOnWriteList<Runnable>()
  private val tracker: DefaultModificationTracker = DefaultModificationTracker()

  init {
    ApplicationManager.getApplication().addApplicationListener(MyApplicationListener(), this)
  }

  override fun reformatFileSync(project: Project, virtualFile: VirtualFile) {
    // remove last error notification
    clearErrorNotification()

    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return
    if (!ensureModifiable(project, virtualFile, document)) {
      return
    }

    // save all ".clang-format"
    saveAllClangFormatFiles()
    val content = ReadAction.compute<String, RuntimeException> { document.text }
      .toByteArray(StandardCharsets.UTF_8)
    val replacements = computeReplacementsWithProgress(project, virtualFile, content)

    // Apply replacements
    if (replacements != null) {
      applyReplacementsWithCommand(project, content, document, replacements)
    }
  }

  override fun getBuiltinPath(): BuiltinPath? {
    val tempDir = Paths.get(PathManager.getPluginTempPath(), "clang-format-tools")
    val version = ClangFormatCommons.readBuiltInVersion()
    val versionMarkerString = "version-$version-${SystemInfo.OS_NAME}-${SystemInfo.OS_ARCH}"
    val versionMarker = tempDir.resolve("version.txt")

    val outputFilename = if (SystemInfo.isWindows) "clang-format.exe" else "clang-format"
    val outputFile = tempDir.resolve(outputFilename)

    val currentVersion = runCatching { versionMarker.toFile().readText() }

    // Check if the file exists
    if (Files.exists(outputFile) && Files.isExecutable(outputFile) && currentVersion.isSuccess && currentVersion.getOrNull() == versionMarkerString) {
      return BuiltinPath(outputFile.toString(), version)
    }

    // Copy the file
    val inputStream = ClangFormatCommons.getClangFormatPathFromResources() ?: return null
    Files.createDirectories(tempDir)
    Files.copy(inputStream, outputFile, StandardCopyOption.REPLACE_EXISTING)

    // Make the file executable
    if (!SystemInfo.isWindows) {
      outputFile.toFile().setExecutable(true)
    }

    // Write the version
    versionMarker.toFile().writeText(versionMarkerString)

    tracker.incModificationCount()

    return BuiltinPath(outputFile.toString(), version)
  }

  override fun getBuiltinPathTracker(): ModificationTracker {
    return tracker
  }

  override fun reformatInBackground(project: Project, virtualFile: VirtualFile) {
    // remove last error notification
    clearErrorNotification()

    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return
    if (!ensureModifiable(project, virtualFile, document)) {
      return
    }

    // save all ".clang-format"
    saveAllClangFormatFiles()

    // Read the file content
    val stamp = document.modificationStamp
    val fileName = ClangFormatCommons.getFileName(virtualFile)
    val content = runReadAction { document.immutableCharSequence }

    runTaskAsync(project) { indicator ->
      // cancel the operation if the document is changed
      val canceller = Runnable {
        if (document.modificationStamp != stamp) {
          // cancel operation when the document is modified
          indicator.cancel()
        }
      }

      val contentAsByteArray = content.toString().toByteArray()

      try {
        afterWriteActionFinished.add(canceller)
        val replacements = computeReplacementsWithError(project, contentAsByteArray, fileName, virtualFile.name)
        if (replacements != null) {
          ApplicationManager.getApplication().invokeAndWait {
            runWriteAction {
              if (stamp == document.modificationStamp) {
                applyReplacementsWithCommand(project, contentAsByteArray, document, replacements)
              }
            }
          }
        }
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
      finally {
        afterWriteActionFinished.remove(canceller)
      }
    }
  }

  private fun computeReplacementsWithProgress(
    project: Project,
    file: VirtualFile,
    content: ByteArray
  ): ClangFormatResponse? {
    val replacementsRef = Ref<ClangFormatResponse>()
    val fileName = ClangFormatCommons.getFileName(file)
    ProgressManager.getInstance().runProcessWithProgressSynchronously({
      replacementsRef.set(
        computeReplacementsWithError(
          project,
          content,
          fileName,
          file.name
        )
      )
    }, message("error.clang-format.formatting"), true, project)
    return replacementsRef.get()
  }

  private fun runTaskAsync(project: Project, fn: (indicator: ProgressIndicator) -> Unit) {
    val task = object : Task.Backgroundable(project, message("error.clang-format.formatting"), true) {
      override fun run(indicator: ProgressIndicator) {
        fn(indicator)
      }
    }
    ProgressManager.getInstance().run(task)
  }

  private fun computeReplacementsWithError(
    project: Project,
    content: ByteArray,
    fileName: String,
    fileSmallName: String
  ): ClangFormatResponse? {
    return try {
      executeClangFormat(project, content, fileName)
    }
    catch (e: ProcessCanceledException) {
      null
    }
    catch (e: ClangValidationError) {
      LOG.warn("Cannot format document due to validation error", e)
      showFormatError(
        project,
        e.description,
        fileSmallName,
        e.getFileNavigatable()
      )
      null
    }
    catch (e: ClangFormatError) {
      LOG.warn("Cannot format document", e)
      showFormatError(project, e.message, fileSmallName, null)
      null
    }
    catch (e: ExecutionException) {
      LOG.warn("Cannot format document", e)
      showFormatError(project, e.message, fileSmallName, null)
      null
    }
    catch (e: Exception) {
      LOG.warn("Cannot format document", e)
      showFormatError(project, "Unknown error", fileSmallName, null)
      null
    }
  }

  @RequiresEdt
  private fun saveAllClangFormatFiles() {
    for (document in ClangFormatCommons.getUnsavedClangFormats()) {
      FileDocumentManager.getInstance().saveDocument(document)
    }
  }

  private fun showFormatError(
    project: Project?,
    content: String?,
    fileSmallName: String,
    openStyle: Navigatable?
  ) {
    val title = message("error.clang-format.failed", fileSmallName)
    val notification = Notification(ClangFormatService.GROUP_ID, title, content!!, NotificationType.ERROR)
    if (openStyle != null) {
      notification.addAction(NotificationAction.createSimple(message("error.clang-format.open.style")) {
        openStyle.navigate(true)
      })
    }
    Notifications.Bus.notify(notification, project)
    errorNotification.getAndSet(notification)?.expire()
  }

  override fun clearErrorNotification() {
    errorNotification.getAndSet(null)?.expire()
  }

  override fun validatePath(path: String): String {
    val file = File(path)
    if (!file.exists() || !file.canExecute()) {
      throw ClangFormatError("Invalid clang-format path")
    }
    LOG.info("Validating path: $path")
    val commandLine = ClangFormatCommons.createCommandLine(path)
    commandLine.addParameter("--version")
    try {
      val output = ProcessUtils.executeProgram(commandLine, ByteArray(0))
      LOG.info("Output: ${output.stdout}")
      if (output.exitCode != 0) {
        throw ClangExitCode(output.exitCode)
      }

      // Check if the output contains "clang-format"
      if (!output.stdout.contains("clang-format")) {
        throw ClangFormatError("Invalid clang-format path")
      }

      return output.stdout.trim()
    }
    catch (e: ExecutionException) {
      throw ClangFormatError("Invalid clang-format path")
    }
  }

  override val clangFormatPath: String?
    get() {
      val config = service<ClangFormatConfig>()
      var path: String? = null
      when (config.state.location) {
        ClangFormatToUse.BUILTIN -> {
          val builtinPath = getBuiltinPath()
          if (builtinPath != null) {
            path = builtinPath.path
          }
        }

        ClangFormatToUse.CUSTOM -> {
          if (!config.state.customPath.isNullOrBlank()) {
            path = config.state.customPath!!.trim()
          }
        }

        ClangFormatToUse.AUTO_DETECT -> {
          path = detectFromPath()
        }
      }
      if (path != null && File(path).exists() && File(path).canExecute()) {
        return path
      }
      return null
    }

  override fun mayBeFormatted(file: PsiFile, inCaseOfStyleError: Boolean): Boolean {
    if (!service<ClangFormatConfig>().state.enabled) {
      return false
    }
    val virtualFile = file.originalFile.virtualFile
    if (ClangFormatCommons.isUnconditionallyNotSupported(virtualFile)) {
      // invalid virtual file
      return false
    }
    val formatStyleService = service<ClangFormatStyleService>()
    if (!formatStyleService.isThereStyleForFile(virtualFile)) {
      // no ".clang-format" file found.
      return false
    }
    val formatStyle = try {
      formatStyleService.getRawFormatStyle(file)
    }
    catch (e: ClangValidationError) {
      // the configuration file contains errors
      return inCaseOfStyleError
    }
    catch (e: ClangFormatError) {
      return false
    }
    val language = formatStyle["Language"]
    val languageStr = language?.toString()?.trim()
    if (languageStr == "Cpp") {
      // clang-format treat all unknown languages as C++
      // we must ensure that the file is really c++
      if (!ClangFormatCommons.isCppFile(file)) {
        return false
      }
    }
    return true
  }

  override fun detectFromPath(): String? {
    val path = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("clang-format")
    if (path != null && path.canExecute()) {
      return path.absolutePath
    }
    return null
  }

  override fun dispose() {}

  @Throws(ExecutionException::class)
  private fun executeClangFormat(project: Project, content: ByteArray, filename: String): ClangFormatResponse {
    val path = clangFormatPath ?: throw ClangFormatError("Cannot find clang-format")
    val commandLine = ClangFormatCommons.createCommandLine(path)
    commandLine.addParameter("--output-replacements-xml")
    commandLine.addParameter("--assume-filename=$filename")
    LOG.info("Running command: " + commandLine.commandLineString)
    val output = ProcessUtils.executeProgram(commandLine, content)
    if (output.exitCode != 0) {
      LOG.warn(commandLine.exePath + " exited with code " + output.exitCode)
      throw ClangFormatCommons.getException(project, commandLine, output)
    }
    if (output.stdout.isEmpty()) {
      // no replacements
      return ClangFormatResponse()
    }
    return parseClangFormatResponse(output.stdout)
  }

  @RequiresEdt
  private fun applyReplacementsWithCommand(
    project: Project,
    content: ByteArray,
    document: Document,
    replacements: ClangFormatResponse
  ) {
    runWriteAction {
      CommandProcessor.getInstance().executeCommand(project, {
        val executeInBulk =
          document.isInBulkUpdate || replacements.replacements.size > BULK_REPLACE_OPTIMIZATION_CRITERIA
        DocumentUtil.executeInBulk(document, executeInBulk) {
          applyAllReplacements(
            content,
            document,
            replacements
          )
        }
      }, message("error.clang-format.command.name"), null, document)
    }
  }

  /**
   * This procedure is a little tricky as the offsets uses utf-8 encoding for offsets
   */
  private fun applyAllReplacements(content: ByteArray, document: Document, replacements: ClangFormatResponse) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val converter = OffsetConverter(content)
    var accumulator = 0
    for (replacement in replacements.replacements) {
      val startOffset = converter.toUtf16(replacement.offset)
      val endOffset = converter.toUtf16(replacement.offset + replacement.length)
      val oldStringLengthUtf16 = endOffset - startOffset
      document.replaceString(accumulator + startOffset, accumulator + endOffset, replacement.value)
      accumulator += replacement.value.length - oldStringLengthUtf16
    }
  }

  private fun ensureModifiable(project: Project, file: VirtualFile, document: Document): Boolean {
    if (FileDocumentManager.getInstance().requestWriting(document, project)) {
      return true
    }
    Messages.showMessageDialog(
      project, CoreBundle.message("cannot.modify.a.read.only.file", file.name),
      CodeInsightBundle.message("error.dialog.readonly.file.title"),
      Messages.getErrorIcon()
    )
    return false
  }

  private inner class MyApplicationListener : ApplicationListener {
    override fun afterWriteActionFinished(action: Any) {
      for (cancellation in afterWriteActionFinished) {
        cancellation.run()
      }
    }
  }
}
