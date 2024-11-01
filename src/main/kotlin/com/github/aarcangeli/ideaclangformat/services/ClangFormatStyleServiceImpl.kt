package com.github.aarcangeli.ideaclangformat.services

import com.github.aarcangeli.ideaclangformat.MyBundle
import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatNotFound
import com.github.aarcangeli.ideaclangformat.utils.ClangFormatCommons
import com.github.aarcangeli.ideaclangformat.utils.ProcessUtils
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.FixedHashMap
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.io.File

private val LOG = Logger.getInstance(ClangFormatStyleServiceImpl::class.java)
private val CLANG_STYLE = Key.create<CachedValue<Pair<Map<String, Any>?, ClangFormatError?>>>("CLANG_STYLE")

class ClangFormatStyleServiceImpl : ClangFormatStyleService, Disposable {
  private val cache: MutableMap<VirtualFile, List<VirtualFile>> = FixedHashMap(100)

  init {
    VirtualFileManager.getInstance().addAsyncFileListener(CacheFileWatcher(), this)
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
    saveUnsavedClangFormatFiles()
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

  private fun saveUnsavedClangFormatFiles() {
    // save changed documents
    val unsavedClangFormats = ClangFormatCommons.getUnsavedClangFormats()
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

  private class FormatStyleProvider(private val service: ClangFormatStyleServiceImpl, private val psiFile: PsiFile) :
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
      val clangFormat = service.getClangFormatVirtualPath() ?: throw ClangFormatNotFound()
      dependencies.add(clangFormat)
      try {
        val commandLine = ClangFormatCommons.createCommandLine(clangFormat.path)
        commandLine.addParameter("--dump-config")
        commandLine.addParameter("-assume-filename=" + getFileName(virtualFile))
        LOG.info("Running command: " + commandLine.commandLineString)
        val programOutput = ProcessUtils.executeProgram(commandLine)
        if (programOutput.exitCode != 0) {
          throw ClangFormatCommons.getException(psiFile.project, commandLine, programOutput)
        }
        try {
          val result = Yaml().load<Map<String, Any>>(programOutput.stdout)
          if (result is Map<*, *>) {
            return result
          }
        }
        catch (ignored: YAMLException) {
        }
        throw ClangFormatError(MyBundle.message("error.clang-format.error.dump.not.yaml", programOutput.stdout))
      }
      catch (e: ExecutionException) {
        LOG.warn("Cannot dump clang-format configuration", e)
        throw ClangFormatError("Cannot get clang-format configuration", e)
      }
    }
  }

  // drop caches when a file is created or deleted
  private inner class CacheFileWatcher : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
      if (isThereAnyChangeInClangFormat(events)) {
        return object : AsyncFileListener.ChangeApplier {
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
  }
}
