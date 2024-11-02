package com.github.aarcangeli.ideaclangformat.experimental

import com.github.aarcangeli.ideaclangformat.ClangFormatConfig
import com.github.aarcangeli.ideaclangformat.MyBundle
import com.github.aarcangeli.ideaclangformat.exceptions.ClangFormatError
import com.github.aarcangeli.ideaclangformat.exceptions.ClangValidationError
import com.github.aarcangeli.ideaclangformat.services.ClangFormatService
import com.github.aarcangeli.ideaclangformat.services.ClangFormatStyleService
import com.github.aarcangeli.ideaclangformat.utils.ClangFormatCommons
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.IndentStatusBarUIContributor
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class ClangFormatStyleSettingsModifier : CodeStyleSettingsModifier {
  val stateConfig = service<ClangFormatConfig>().state

  override fun modifySettings(settings: TransientCodeStyleSettings, file: PsiFile): Boolean {
    if (!stateConfig.enabled) {
      return false
    }
    if (ClangFormatCommons.isUnconditionallyNotSupported(file)) {
      return false
    }

    val formatService = service<ClangFormatService>()
    val formatStyleService = service<ClangFormatStyleService>()

    settings.addDependency(formatStyleService.makeDependencyTracker(file))

    if (!formatService.mayBeFormatted(file, true)) {
      // clang format disabled for this file
      file.putUserData(LAST_PROVIDED_SETTINGS, null)
      return false
    }

    try {
      val clangFormatStyle = ClangFormatStyle(formatStyleService.getRawFormatStyle(file))
      file.putUserData(LAST_PROVIDED_SETTINGS, clangFormatStyle)
      clangFormatStyle.apply(settings)
      return true
    }
    catch (e: ClangValidationError) {
      // configuration broken, re-use last provided settings
      val clangFormatStyle = file.getUserData(LAST_PROVIDED_SETTINGS)
      if (clangFormatStyle != null) {
        clangFormatStyle.apply(settings)
        return true
      }
    }
    catch (e: ClangFormatError) {
      // ignore other error
    }

    return false
  }

  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return MyBundle.message("error.clang-format.name")
  }

  override fun getStatusBarUiContributor(settings: TransientCodeStyleSettings): CodeStyleStatusBarUIContributor {
    return object : IndentStatusBarUIContributor(settings.indentOptions) {
      override fun getTooltip(): String {
        val builder = HtmlBuilder()
        builder.append(MyBundle.message("error.clang-format.status.hint"))
          .br()
        builder
          .append("Indent:")
          .append(" ")
          .append(
            HtmlChunk.tag("b").addText(
              getIndentInfo(
                indentOptions
              )
            )
          )
          .br()
        builder
          .append("Tab Size: ")
          .append(" ")
          .append(HtmlChunk.tag("b").addText(indentOptions.TAB_SIZE.toString()))
          .br()
        builder
          .append("Right Margin: ")
          .append(" ")
          .append(HtmlChunk.tag("b").addText(settings.defaultRightMargin.toString()))
          .br()
        return builder.wrapWith("html").toString()
      }

      override fun getHint(): String {
        return MyBundle.message("error.clang-format.status.hint")
      }

      override fun areActionsAvailable(file: VirtualFile): Boolean {
        return true
      }

      override fun isShowFileIndentOptionsEnabled(): Boolean {
        return false
      }

      override fun getActions(file: PsiFile): Array<AnAction> {
        return arrayOf(OpenClangConfigAction())
      }

      override fun createDisableAction(project: Project): AnAction {
        return DumbAwareAction.create(MyBundle.message("clang-format.disable")) {
          stateConfig.enabled = false
          CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
          ClangFormatDisabledNotification(project).notify(project)
        }
      }

      override fun getIcon(): Icon {
        // this is the same of ".editorconfig"
        return AllIcons.Ide.ConfigFile
      }
    }
  }

  private class ClangFormatDisabledNotification(project: Project) : Notification(
    ClangFormatService.GROUP_ID,
    MyBundle.message("clang-format.disabled.notification"),
    "",
    NotificationType.INFORMATION
  ) {
    init {
      addAction(ReEnableAction(project, this))
      addAction(ShowEditorConfigOption(ApplicationBundle.message("code.style.indent.provider.notification.settings")))
    }
  }

  private class ReEnableAction(
    private val myProject: Project,
    private val myNotification: Notification
  ) : DumbAwareAction(ApplicationBundle.message("code.style.indent.provider.notification.re.enable")) {
    override fun actionPerformed(e: AnActionEvent) {
      service<ClangFormatConfig>().state.enabled = true
      CodeStyleSettingsManager.getInstance(myProject).notifyCodeStyleSettingsChanged()
      myNotification.expire()
    }
  }

  private class ShowEditorConfigOption(text: @Nls String?) : DumbAwareAction(text) {
    override fun actionPerformed(e: AnActionEvent) {
      ShowSettingsUtilImpl.showSettingsDialog(e.project, "preferences.sourceCode", null)
    }
  }

  private class OpenClangConfigAction : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
      e.presentation.text = MyBundle.message("error.clang-format.status.open")
      e.presentation.isEnabled = isEnabled(e)
    }

    private fun isEnabled(e: AnActionEvent): Boolean {
      val project = e.project
      val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
      if (project != null && virtualFile != null) {
        return service<ClangFormatStyleService>().isThereStyleForFile(virtualFile)
      }
      return false
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project
      val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
      if (project != null && virtualFile != null) {
        val styleFile: VirtualFile? = service<ClangFormatStyleService>().getStyleFile(virtualFile)
        if (styleFile != null) {
          PsiNavigationSupport.getInstance().createNavigatable(project, styleFile, -1).navigate(true)
        }
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  /**
   * Contains a small subset of clang-format parameters.
   * This is used to apply the settings to the IDE.
   */
  private class ClangFormatStyle(formatStyle: Map<String, Any>) {
    private val columnLimit: Int = getInt(formatStyle, "ColumnLimit")
    private val indentWidth: Int = getInt(formatStyle, "IndentWidth")
    private val tabWidth: Int = getInt(formatStyle, "TabWidth")

    // fixme: unsupported values "ForIndentation", "ForContinuationAndIndentation", etc
    private val useTab: Boolean = formatStyle["UseTab"] != null && !formatStyle["UseTab"].toString()
      .equals("false", ignoreCase = true) && !formatStyle["UseTab"].toString().equals("never", ignoreCase = true)

    fun apply(settings: TransientCodeStyleSettings) {
      if (columnLimit > 0) {
        settings.defaultRightMargin = columnLimit
      }
      if (indentWidth > 0) {
        settings.indentOptions.INDENT_SIZE = indentWidth
      }
      if (tabWidth > 0) {
        settings.indentOptions.TAB_SIZE = tabWidth
      }
      settings.indentOptions.USE_TAB_CHARACTER = useTab
    }
  }

  companion object {
    private val LAST_PROVIDED_SETTINGS = Key.create<ClangFormatStyle>("WAS_FILE_SUPPORTED")

    private fun getInt(formatStyle: Map<String, Any>, tag: String): Int {
      val value = formatStyle[tag]
      return if (value is Int) value else -1
    }
  }
}
