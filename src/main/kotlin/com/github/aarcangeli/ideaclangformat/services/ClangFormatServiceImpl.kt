package com.github.aarcangeli.ideaclangformat.services

import com.github.aarcangeli.ideaclangformat.MyBundle.message
import com.github.aarcangeli.ideaclangformat.exceptions.ClangExitCodeError
import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatNotFound
import com.github.aarcangeli.ideaclangformat.exceptions.ClangMissingLanguageException
import com.github.aarcangeli.ideaclangformat.utils.ClangFormatCommons
import com.github.aarcangeli.ideaclangformat.utils.OffsetConverter
import com.intellij.build.FileNavigatable
import com.intellij.build.FilePosition
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.core.CoreBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.*
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.FixedHashMap
import org.jetbrains.annotations.NonNls
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import kotlin.Pair

private val LOG = Logger.getInstance(ClangFormatServiceImpl::class.java)

/**
 * See class ApplyChangesState
 */
private const val BULK_REPLACE_OPTIMIZATION_CRITERIA = 1000

class ClangFormatServiceImpl : ClangFormatService, Disposable {
  private val errorNotification = AtomicReference<Notification?>()
  private val wasClangFormatSupported = Key.create<Boolean>("WAS_CLANG_FORMAT_SUPPORTED")
  private val afterWriteActionFinished = ContainerUtil.createLockFreeCopyOnWriteList<Runnable>()
  private val cache: MutableMap<VirtualFile, List<VirtualFile>> = FixedHashMap(100)

  init {
    ApplicationManager.getApplication().addApplicationListener(MyApplicationListener(), this)
    VirtualFileManager.getInstance().addAsyncFileListener(ClangFormatCacheManagment(), this)
  }

  override fun reformatFileSync(project: Project, virtualFile: VirtualFile) {
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
      applyReplacementsWithCommand(project, content, document, replacements)
    }

    // remove last error notification
    clearLastNotification()
  }

  override fun reformatInBackground(project: Project, virtualFile: VirtualFile) {
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
              if (stamp == document.modificationStamp && replacements.replacements != null) {
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
    for (document in getUnsavedClangFormats()) {
      FileDocumentManager.getInstance().saveDocument(document)
    }
  }

  private fun getUnsavedClangFormats(): Array<Document> {
    val documents = ArrayList<Document>()
    for (document in FileDocumentManager.getInstance().unsavedDocuments) {
      val file = FileDocumentManager.getInstance().getFile(document) ?: continue
      if (ClangFormatCommons.isClangFormatFile(file.name) && document.isWritable) {
        documents.add(document)
      }
    }
    return documents.toTypedArray()
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

  private fun getClangFormatVirtualPath(): VirtualFile? {
    val clangFormatPath = findClangFormatPath()
    if (clangFormatPath != null) {
      return VfsUtil.findFileByIoFile(clangFormatPath, true)
    }
    return null
  }

  override fun getRawFormatStyle(psiFile: PsiFile): Map<String, Any> {
    // save changed documents
    saveUnchangedClangFormatFiles()
    val result = CachedValuesManager.getCachedValue(psiFile, CLANG_STYLE, FormatStyleProvider(this, psiFile))
    if (result.second != null) {
      throw result.second!!
    }
    return result.first!!
  }

  override fun makeDependencyTracker(file: PsiFile): ModificationTracker {
    val virtualFile = getVirtualFile(file)
      ?: return ModificationTracker.NEVER_CHANGED
    val oldFiles = getClangFormatFiles(virtualFile)
    val oldStamps = oldFiles.stream()
      .mapToLong { it: VirtualFile -> it.modificationStamp }
      .toArray()
    val documentStamp = oldFiles.stream()
      .mapToLong { it: VirtualFile -> findDocumentStamp(it) }
      .toArray()
    return ModificationTracker {
      val newFiles = getClangFormatFiles(virtualFile)
      if (newFiles.size != oldFiles.size) {
        // added or removed file
        return@ModificationTracker -1
      }
      for (i in newFiles.indices) {
        if (oldFiles[i] != newFiles[i]) {
          return@ModificationTracker -1
        }
        if (oldStamps[i] != newFiles[i].modificationStamp) {
          return@ModificationTracker -1
        }
        if (documentStamp[i] != findDocumentStamp(newFiles[i])) {
          return@ModificationTracker -1
        }
      }
      1
    }
  }

  private fun findDocumentStamp(file: VirtualFile): Long {
    val cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file)
    return cachedDocument?.modificationStamp ?: -1
  }

  override fun getStyleFile(virtualFile: VirtualFile): VirtualFile? {
    val formatFiles = getClangFormatFiles(virtualFile)
    if (formatFiles.isNotEmpty()) {
      return formatFiles[0]
    }
    return null
  }

  override fun mayBeFormatted(file: PsiFile): Boolean {
    val virtualFile = file.originalFile.virtualFile
    if (ClangFormatCommons.isUnconditionallyNotSupported(virtualFile)) {
      // invalid virtual file
      return false
    }
    if (getStyleFile(virtualFile) == null) {
      // no ".clang-format" file found.
      // this is not a real issue for clang-format, but when no format is provided
      // the editor shouldn't modify the appearance with llvm's default settings
      file.putUserData(wasClangFormatSupported, null)
      return false
    }
    val formatStyle = try {
      getRawFormatStyle(file)
    }
    catch (e: ClangExitCodeError) {
      // the configuration is (maybe temporary) broken. We reuse the answer of last invocation until the configuration is fixed
      return java.lang.Boolean.TRUE == file.getUserData(wasClangFormatSupported)
    }
    catch (e: ClangFormatError) {
      // the configuration is broken or the file language is not supported in ".clang-format"
      file.putUserData(wasClangFormatSupported, null)
      return false
    }
    val language = formatStyle["Language"]
    val languageStr = language?.toString()?.trim { it <= ' ' } ?: ""
    if (languageStr == "Cpp") {
      // for clang, Cpp is a fallback for any file.
      // we must ensure that the file is really c++
      if (!ClangFormatCommons.isCppFile(file)) {
        file.putUserData(wasClangFormatSupported, null)
        return false
      }
    }
    file.putUserData(wasClangFormatSupported, true)
    return true
  }

  private fun saveUnchangedClangFormatFiles() {
    // save changed documents
    val unsavedClangFormats = getUnsavedClangFormats()
    if (unsavedClangFormats.isNotEmpty()) {
      ApplicationManager.getApplication().invokeLater {
        WriteAction.run<RuntimeException> {
          for (document in unsavedClangFormats) {
            FileDocumentManager.getInstance().saveDocument(document)
          }
        }
      }
    }
  }

  override fun dispose() {}

  @Throws(ExecutionException::class)
  private fun executeClangFormat(project: Project, content: ByteArray, filename: String): ClangFormatResponse {
    val commandLine = createCompileCommand(clangFormatPath)
    commandLine.addParameter("-output-replacements-xml")
    commandLine.addParameter("-assume-filename=$filename")
    val output = executeProgram(content, commandLine)
    if (output.exitCode != 0) {
      LOG.warn(commandLine.exePath + " exited with code " + output.exitCode)
      throw getException(project, commandLine, output)
    }
    if (output.stdout.isEmpty()) {
      // no replacements
      return ClangFormatResponse()
    }
    return parseClangFormatResponse(output.stdout)
  }

  @Throws(ClangExitCodeError::class)
  private fun getException(project: Project, commandLine: GeneralCommandLine, output: ProcessOutput): ClangFormatError {
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
          return ClangExitCodeError(
            description,
            FileNavigatable(project, FilePosition(fileName, lineNumber - 1, column - 1))
          )
        }
      }
      catch (ignored: NumberFormatException) {
        // in case of overflow
      }
    }
    if (stderr.trim { it <= ' ' }.isEmpty()) {
      // nothing on stderr, we use stdout instead
      stderr = output.stdout
    }
    val description = """Exit code ${output.exitCode} from ${commandLine.commandLineString}
$stderr"""
    return ClangFormatError(description)
  }

  @RequiresEdt
  private fun applyReplacementsWithCommand(
    project: Project,
    content: ByteArray,
    document: Document,
    replacements: ClangFormatResponse
  ) {
    assert(replacements.replacements != null)
    CommandProcessor.getInstance().executeCommand(project, {
      val executeInBulk =
        document.isInBulkUpdate || replacements.replacements!!.size > BULK_REPLACE_OPTIMIZATION_CRITERIA
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

  private fun dropCaches() {
    synchronized(cache) { cache.clear() }
  }

  @RequiresReadLock
  private fun getClangFormatFiles(file: VirtualFile): List<VirtualFile> {
    synchronized(cache) {
      return cache.computeIfAbsent(file) { inFile: VirtualFile? ->
        val files: MutableList<VirtualFile> = ArrayList()
        var it = inFile
        while (it != null) {
          val child = it.findChild(".clang-format")
          if (child != null) {
            // read files in
            files.add(child)
          }
          val childAlt = it.findChild("_clang-format")
          if (childAlt != null) {
            // read files in
            files.add(childAlt)
          }
          it = it.parent
        }
        files
      }
    }
  }

  private class FormatStyleProvider(private val service: ClangFormatServiceImpl, private val psiFile: PsiFile) :
    CachedValueProvider<Pair<Map<String, Any>?, ClangFormatError?>> {
    override fun compute(): CachedValueProvider.Result<Pair<Map<String, Any>?, ClangFormatError?>> {
      val dependencies: MutableList<Any?> = ArrayList()
      return try {
        val result = computeFormat(dependencies)
        CachedValueProvider.Result.create(Pair(result, null), *dependencies.toTypedArray())
      }
      catch (e: ClangFormatError) {
        CachedValueProvider.Result.create(Pair(null, e), *dependencies.toTypedArray())
      }
    }

    private fun computeFormat(dependencies: MutableList<Any?>): Map<String, Any> {
      dependencies.add(service.makeDependencyTracker(psiFile))
      val virtualFile = getVirtualFile(psiFile)
      if (virtualFile == null) {
        LOG.warn("Missing filename for $psiFile")
        throw ClangFormatError("Cannot get clang-format configuration")
      }
      val clangFormat = service.getClangFormatVirtualPath()
        ?: throw ClangFormatNotFound(message("error.clang-format.error.not-found"))
      dependencies.add(clangFormat)
      try {
        val commandLine = createCompileCommand(clangFormat.path)
        commandLine.addParameter("--dump-config")
        commandLine.addParameter("-assume-filename=" + getFileName(virtualFile))
        val programOutput = executeProgram(null, commandLine)
        if (programOutput.exitCode != 0) {
          throw service.getException(psiFile.project, commandLine, programOutput)
        }
        try {
          val result = Yaml().load<Map<String, Any>>(programOutput.stdout)
          if (result is Map<*, *>) {
            return result
          }
        }
        catch (ignored: YAMLException) {
        }
        throw ClangFormatError(message("error.clang-format.error.dump.not.yaml", programOutput.stdout))
      }
      catch (e: ExecutionException) {
        LOG.warn("Cannot dump clang-format configuration", e)
        throw ClangFormatError("Cannot get clang-format configuration", e)
      }
    }
  }

  private inner class MyApplicationListener : ApplicationListener {
    override fun afterWriteActionFinished(action: Any) {
      for (cancellation in afterWriteActionFinished) {
        cancellation.run()
      }
    }
  }

  // drop caches when a file is created or deleted
  private inner class ClangFormatCacheManagment : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): ChangeApplier? {
      if (isThereAnyChangeInClangFormat(events)) {
        return object : ChangeApplier {
          override fun afterVfsChange() {
            dropCaches()
          }
        }
      }
      return null
    }

    private fun isThereAnyChangeInClangFormat(events: List<VFileEvent>): Boolean {
      for (event in events) {
        if (event is VFileCreateEvent) {
          if (ClangFormatCommons.isClangFormatFile(event.childName)) {
            return true
          }
        }
        else if (event is VFileCopyEvent) {
          if (ClangFormatCommons.isClangFormatFile(event.file.name) ||
            ClangFormatCommons.isClangFormatFile(event.newChildName)
          ) {
            return true
          }
        }
        else if (event is VFileDeleteEvent) {
          if (ClangFormatCommons.isClangFormatFile(event.file.name)) {
            return true
          }
        }
        else if (event is VFileMoveEvent) {
          if (ClangFormatCommons.isClangFormatFile(event.file.name)) {
            return true
          }
        }
        else if (event is VFilePropertyChangeEvent) {
          when (event.propertyName) {
            VirtualFile.PROP_NAME ->
              if (ClangFormatCommons.isClangFormatFile(event.oldValue.toString()) ||
                ClangFormatCommons.isClangFormatFile(event.newValue.toString())
              ) {
                return true
              }

            VirtualFile.PROP_ENCODING,
            VirtualFile.PROP_SYMLINK_TARGET ->
              if (ClangFormatCommons.isClangFormatFile(event.file.name)) {
                return true
              }
          }
        }
      }
      return false
    }
  }

  companion object {
    private const val TIMEOUT = 10000
    private val CLANG_STYLE = Key.create<CachedValue<Pair<Map<String, Any>?, ClangFormatError?>>>("CLANG_STYLE")
    private val CLANG_ERROR_PATTERN = Pattern.compile(
      "(?<FileName>(?:[a-zA-Z]:|/)[^<>|?*:\\t]+):(?<LineNumber>\\d+):(?<Column>\\d+)\\s*:\\s*(?<Type>\\w+):\\s*(?<Message>.*)"
    )

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

    private fun getVirtualFile(file: PsiFile): VirtualFile? {
      var virtualFile = file.originalFile.virtualFile
      if (virtualFile is LightVirtualFile) {
        virtualFile = virtualFile.originalFile
      }
      return if (virtualFile != null && virtualFile.isInLocalFileSystem) {
        virtualFile
      }
      else null
    }

    private fun createCompileCommand(clangFormatPath: String): GeneralCommandLine {
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

    @Throws(ExecutionException::class)
    private fun executeProgram(content: ByteArray?, commandLine: GeneralCommandLine): ProcessOutput {
      val handler = CapturingProcessHandler(commandLine)

      // write and close output stream on pooled thread
      var writerFuture: Future<*>? = null
      if (content != null) {
        writerFuture = ApplicationManager.getApplication().executeOnPooledThread {
          try {
            handler.processInput.use { out -> out.write(content) }
          }
          catch (ignored: IOException) {
          }
        }
      }
      val output: ProcessOutput
      try {
        output = ProgressIndicatorUtils.awaitWithCheckCanceled(
          ApplicationManager.getApplication().executeOnPooledThread<ProcessOutput> {
            handler.runProcess(
              TIMEOUT, true
            )
          })
        if (writerFuture != null) {
          ProgressIndicatorUtils.awaitWithCheckCanceled(writerFuture)
        }
      }
      finally {
        handler.destroyProcess()
      }
      return output
    }

    private fun isWinShellScript(command: @NonNls String?): Boolean {
      return endsWithIgnoreCase(command, ".cmd") || endsWithIgnoreCase(command, ".bat")
    }

    private fun endsWithIgnoreCase(str: String?, suffix: String): Boolean {
      return str!!.regionMatches(str.length - suffix.length, suffix, 0, suffix.length, ignoreCase = true)
    }
  }
}
