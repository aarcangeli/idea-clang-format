package com.github.aarcangeli.ideaclangformat

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Consumer
import java.awt.Component

private const val GH_HOME = "https://github.com/aarcangeli/idea-clang-format"

class GithubErrorHandler : ErrorReportSubmitter() {
  override fun getReportActionText(): String {
    return MyBundle.message("crash.report.action")
  }

  override fun submit(
    events: Array<IdeaLoggingEvent>,
    additionalInfo: String?,
    parentComponent: Component,
    consumer: Consumer<in SubmittedReportInfo?>
  ): Boolean {
    val url = GH_HOME +
      "/issues/new?labels=exception" +
      "&title=" + encodeURIComponent(makeTitle(events)) +
      "&body=" + encodeURIComponent(makeIssueBody(events, additionalInfo))
    BrowserUtil.browse(url)
    return false
  }

  companion object {
    private const val HEX = "0123456789ABCDEF"

    // GitHub url limit is ~ 8k chars, but windows limit link about to 2k
    private const val MAX_TRACE_SIZE = 1400

    private fun makeIssueBody(events: Array<IdeaLoggingEvent>, additionalInfo: String?): String {
      val body = StringBuilder()
      body.append("### Description\n")
      body.append("<!-- Comment on what were you doing when the exception occurred -->\n")
      if (additionalInfo != null && additionalInfo.trim().isNotEmpty()) {
        body.append(additionalInfo.trim()).append("\n")
      }
      body.append("\n")

      body.append("### Stacktrace\n")
      body.append("<!-- Please paste the full stacktrace from the IDEA error popup -->\n")
      val stacktrace = getStacktrace(events)
      if (stacktrace != null) {
        body.append("```\n").append(stacktrace).append("```\n")
        body.append("\n")
      }

      body.append("### Version and Environment Details\n")
      body.append(String.format("Plugin version: %s\n", getPluginVersion()))
      body.append(String.format("Operating System: %s\n", SystemInfo.getOsNameAndVersion()))
      body.append(String.format("IDE version: %s\n", ApplicationInfo.getInstance().fullApplicationName))
      body.append("\n")

      return body.toString().trim { it <= ' ' }
    }

    private fun getPluginVersion(): String {
      val plugin = PluginManager.getPluginByClass(GithubErrorHandler::class.java)
      return plugin?.version ?: "unknown"
    }

    private fun encodeURIComponent(str: String?): String? {
      if (str == null) return null
      val sb = StringBuilder()
      for (c in str.toCharArray()) {
        if (if (c >= 'a') c <= 'z' || c == '~' else if (c >= 'A') c <= 'Z' || c == '_' else if (c >= '0') c <= '9' else c == '-' || c == '.') {
          sb.append(c)
        }
        else if (c == ' ') {
          sb.append("+")
        }
        else if (c.code <= 0xff) {
          sb.append('%')
            .append(HEX[c.code shr 4 and 0xf])
            .append(HEX[c.code and 0xf])
        }
      }
      return sb.toString()
    }

    private fun makeTitle(events: Array<IdeaLoggingEvent>): String {
      for (event in events) {
        val message = event.throwableText
        if (message != null && message.isNotEmpty()) {
          val first = message.lines().first()
          if (first.isNotEmpty()) {
            return "Exception: $first"
          }
        }
      }
      return "Exception: <Fill in title>"
    }

    private fun getStacktrace(events: Array<IdeaLoggingEvent>): String? {
      for (event in events) {
        val text = event.throwableText
        if (text != null && text.isNotEmpty()) {
          val sb = StringBuilder()
          for (line in text.split("\n").dropLastWhile { it.isEmpty() }.toTypedArray()) {
            sb.append(line).append("\n")
            if (sb.length > MAX_TRACE_SIZE) {
              sb.append("\t[...]\n")
              break
            }
          }
          return sb.toString()
        }
      }
      return null
    }
  }
}
