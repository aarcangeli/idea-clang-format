package com.github.aarcangeli.ideaclangformat.services

import com.github.aarcangeli.ideaclangformat.MyBundle.message
import com.github.aarcangeli.ideaclangformat.exceptions.ClangExitCodeError
import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
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
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import java.io.File
import java.nio.charset.StandardCharsets
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

  init {
    ApplicationManager.getApplication().addApplicationListener(MyApplicationListener(), this)
  }

  override fun reformatFileSync(project: Project, virtualFile: VirtualFile) {
    // remove last error notification
    clearLastNotification()

    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return
    if (!ensureModifiable(project, virtualFile, document)) {
      return
    }

    // save all ".clang-format"
    saveAllClangFormatFiles()
    val stamp = document.modificationStamp
    val content = ReadAction.compute<String, RuntimeException> { document.text }
      .toByteArray(StandardCharsets.UTF_8)
    val replacements = computeReplacementsWithProgress(project, virtualFile, content)

    // Apply replacements
    if (replacements != null && stamp == document.modificationStamp) {
      runWriteAction {
        applyReplacementsWithCommand(project, content, document, replacements)
      }
    }
  }

  override fun reformatInBackground(project: Project, virtualFile: VirtualFile) {
    // remove last error notification
    clearLastNotification()

    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return
    if (!ensureModifiable(project, virtualFile, document)) {
      return
    }

    // save all ".clang-format"
    saveAllClangFormatFiles()

    // Read the file content
    val stamp = document.modificationStamp
    val fileName = getFileName(virtualFile)
    val content = ReadAction.compute<String, RuntimeException> { document.text }

    runTaskAsync(project) { indicator ->
      // cancel the operation if the document is changed
      val canceller = Runnable {
        if (document.modificationStamp != stamp) {
          // cancel operation when the document is modified
          indicator.cancel()
        }
      }

      val contentAsByteArray = content.toByteArray(StandardCharsets.UTF_8)

      try {
        afterWriteActionFinished.add(canceller)
        val replacements = computeReplacementsWithError(project, contentAsByteArray, fileName, virtualFile.name)
        if (replacements != null) {
          invokeAndWaitIfNeeded {
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
    val fileName = getFileName(file)
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
    catch (e: ClangExitCodeError) {
      LOG.warn("Cannot format document", e)
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
    val oldNotification = errorNotification.getAndSet(notification)
    oldNotification?.expire()
  }

  private fun clearLastNotification() {
    val oldNotification = errorNotification.getAndSet(null)
    oldNotification?.expire()
  }

  @get:Throws(ClangFormatError::class)
  override val clangFormatPath: String
    get() {
      val path = findClangFormatPath()
      if (path == null || !path.canExecute()) {
        throw ClangFormatError("Cannot find clang-format")
      }
      return path.absolutePath
    }

  private fun findClangFormatPath(): File? {
    return PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("clang-format")
  }

  override fun mayBeFormatted(file: PsiFile): Boolean {
    val virtualFile = file.originalFile.virtualFile
    if (ClangFormatCommons.isUnconditionallyNotSupported(virtualFile)) {
      // invalid virtual file
      return false
    }
    val formatStyleService = service<ClangFormatStyleService>()
    if (formatStyleService.getStyleFile(virtualFile) == null) {
      // no ".clang-format" file found.
      return false
    }
    val formatStyle = try {
      formatStyleService.getRawFormatStyle(file)
    }
    catch (e: ClangExitCodeError) {
      // the configuration file contains errors
      return true
    }
    catch (e: ClangFormatError) {
      return false
    }
    val language = formatStyle["Language"]
    val languageStr = language?.toString()?.trim { it <= ' ' } ?: ""
    if (languageStr == "Cpp") {
      // for clang, Cpp is a fallback for any file.
      // we must ensure that the file is really c++
      if (!ClangFormatCommons.isCppFile(file)) {
        return false
      }
    }
    return true
  }

  override fun dispose() {}

  @Throws(ExecutionException::class)
  private fun executeClangFormat(project: Project, content: ByteArray, filename: String): ClangFormatResponse {
    val commandLine = ClangFormatCommons.createCompileCommand(clangFormatPath)
    commandLine.addParameter("-output-replacements-xml")
    commandLine.addParameter("-assume-filename=$filename")
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

  companion object {
    private fun getFileName(virtualFile: VirtualFile): String {
      var it = virtualFile
      if (it is LightVirtualFile) {
        it = it.originalFile
      }
      if (it.isInLocalFileSystem) {
        return it.path
      }
      return virtualFile.name
    }
  }
}
