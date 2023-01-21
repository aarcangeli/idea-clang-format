package com.github.aarcangeli.ideaclangformat.utils

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import java.io.IOException
import java.util.concurrent.Future

private const val TIMEOUT = 10000

object ProcessUtils {
  @Throws(ExecutionException::class)
  fun executeProgram(commandLine: GeneralCommandLine, content: ByteArray? = null): ProcessOutput {
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

}
